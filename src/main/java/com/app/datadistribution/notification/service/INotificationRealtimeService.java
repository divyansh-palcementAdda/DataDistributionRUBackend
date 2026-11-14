package com.app.datadistribution.notification.service;

import java.util.UUID;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.app.datadistribution.notification.dto.NotificationResponseDTO;

public interface INotificationRealtimeService {

    SseEmitter createEmitter(UUID userId);

    void pushNotification(UUID userId, NotificationResponseDTO notification);

    void broadcast(NotificationResponseDTO notification);

    void closeUserEmitters(UUID userId);
}

