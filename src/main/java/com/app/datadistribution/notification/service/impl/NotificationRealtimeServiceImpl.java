package com.app.datadistribution.notification.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.app.datadistribution.notification.dto.NotificationResponseDTO;
import com.app.datadistribution.notification.service.INotificationRealtimeService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationRealtimeServiceImpl implements INotificationRealtimeService {

    // Keep active emitters per user (supporting multiple tabs/devices per user)
    private final Map<UUID, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    // 15-minute connection timeout (standard for reverse proxy)
    private static final long SSE_TIMEOUT_MS = 15 * 60 * 1000L;

    @Override
    public SseEmitter createEmitter(UUID userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(userId, emitter);
        });
        emitter.onError(e -> {
            emitter.complete();
            removeEmitter(userId, emitter);
        });

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(Map.of("status", "CONNECTED", "userId", userId.toString())));
        } catch (IOException e) {
            log.debug("Failed to send initial SSE event to user {}", userId);
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    @Override
    public void pushNotification(UUID userId, NotificationResponseDTO notification) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> failedEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("NOTIFICATION")
                        .id(notification.getId() != null ? notification.getId().toString() : UUID.randomUUID().toString())
                        .data(notification));
            } catch (Exception e) {
                log.debug("Error delivering SSE notification to user {}: {}", userId, e.getMessage());
                failedEmitters.add(emitter);
            }
        }

        if (!failedEmitters.isEmpty()) {
            emitters.removeAll(failedEmitters);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }

    @Override
    public void broadcast(NotificationResponseDTO notification) {
        userEmitters.keySet().forEach(userId -> pushNotification(userId, notification));
    }

    private void removeEmitter(UUID userId, SseEmitter emitter) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }

    /**
     * Periodic heartbeat/ping every 30 seconds to keep connection alive through Nginx and proxies
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        if (userEmitters.isEmpty()) {
            return;
        }

        userEmitters.forEach((userId, emitters) -> {
            List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("PING").data("ping"));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            if (!deadEmitters.isEmpty()) {
                emitters.removeAll(deadEmitters);
            }
        });
    }
}
