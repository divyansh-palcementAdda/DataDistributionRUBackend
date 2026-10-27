package com.app.datadistribution.integration.website.security;

import com.app.datadistribution.integration.website.config.WebsiteIntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe sliding-window / fixed-interval rate limiter per client IP.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebsiteRateLimiter {

    private final WebsiteIntegrationProperties properties;

    private static class RequestCounter {
        long windowStartMs;
        AtomicInteger count;

        RequestCounter(long windowStartMs, int initialCount) {
            this.windowStartMs = windowStartMs;
            this.count = new AtomicInteger(initialCount);
        }
    }

    private final ConcurrentHashMap<String, RequestCounter> clientBuckets = new ConcurrentHashMap<>();

    /**
     * Checks if the request from the given client key (e.g. IP) is allowed under current rate limits.
     *
     * @param clientIdentifier Client IP or token
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean tryAcquire(String clientIdentifier) {
        if (clientIdentifier == null || clientIdentifier.isBlank()) {
            clientIdentifier = "UNKNOWN_CLIENT";
        }

        int maxPerMinute = properties.getRateLimitPerMinute();
        if (maxPerMinute <= 0) {
            return true; // Rate limiting effectively disabled
        }

        long now = System.currentTimeMillis();
        long oneMinuteMs = 60_000L;

        RequestCounter counter = clientBuckets.compute(clientIdentifier, (key, existing) -> {
            if (existing == null || (now - existing.windowStartMs) > oneMinuteMs) {
                return new RequestCounter(now, 1);
            } else {
                existing.count.incrementAndGet();
                return existing;
            }
        });

        int limitWithBurst = maxPerMinute + Math.max(0, properties.getRateLimitBurst());
        if (counter.count.get() > limitWithBurst) {
            log.warn("Rate limit exceeded for client: count={}, limit={}", counter.count.get(), limitWithBurst);
            return false;
        }

        return true;
    }

    /**
     * Periodic cleanup of stale client buckets every 5 minutes to prevent memory leaks.
     */
    @Scheduled(fixedRate = 300_000)
    public void cleanupStaleBuckets() {
        long now = System.currentTimeMillis();
        long staleThreshold = 120_000L; // 2 minutes
        clientBuckets.entrySet().removeIf(entry -> (now - entry.getValue().windowStartMs) > staleThreshold);
    }
}
