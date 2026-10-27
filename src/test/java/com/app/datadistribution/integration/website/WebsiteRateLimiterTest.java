package com.app.datadistribution.integration.website;

import com.app.datadistribution.integration.website.config.WebsiteIntegrationProperties;
import com.app.datadistribution.integration.website.security.WebsiteRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebsiteRateLimiterTest {

    @Mock
    private WebsiteIntegrationProperties properties;

    @InjectMocks
    private WebsiteRateLimiter rateLimiter;

    @Test
    @DisplayName("Should allow requests within rate limit")
    void shouldAllowRequestsWithinLimit() {
        when(properties.getRateLimitPerMinute()).thenReturn(5);
        when(properties.getRateLimitBurst()).thenReturn(0);

        String ip = "192.168.1.100";
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire(ip), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("Should reject requests exceeding rate limit + burst")
    void shouldRejectWhenExceedingLimit() {
        when(properties.getRateLimitPerMinute()).thenReturn(3);
        when(properties.getRateLimitBurst()).thenReturn(1);

        String ip = "10.0.0.50";
        // Total allowed = 3 + 1 = 4
        assertTrue(rateLimiter.tryAcquire(ip));
        assertTrue(rateLimiter.tryAcquire(ip));
        assertTrue(rateLimiter.tryAcquire(ip));
        assertTrue(rateLimiter.tryAcquire(ip));

        // 5th request should be blocked
        assertFalse(rateLimiter.tryAcquire(ip), "5th request should be rejected by rate limiter");
    }

    @Test
    @DisplayName("Different client IPs should have separate rate limits")
    void shouldTrackClientsIndependently() {
        when(properties.getRateLimitPerMinute()).thenReturn(2);
        when(properties.getRateLimitBurst()).thenReturn(0);

        String ip1 = "192.168.1.1";
        String ip2 = "192.168.1.2";

        assertTrue(rateLimiter.tryAcquire(ip1));
        assertTrue(rateLimiter.tryAcquire(ip1));
        assertFalse(rateLimiter.tryAcquire(ip1));

        // ip2 should still be allowed
        assertTrue(rateLimiter.tryAcquire(ip2));
        assertTrue(rateLimiter.tryAcquire(ip2));
        assertFalse(rateLimiter.tryAcquire(ip2));
    }
}
