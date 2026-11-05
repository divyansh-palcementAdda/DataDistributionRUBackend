package com.app.datadistribution.notification.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.app.datadistribution.notification.dto.NotificationCountDTO;
import com.app.datadistribution.notification.dto.NotificationCreateRequest;
import com.app.datadistribution.notification.dto.NotificationPageResponseDTO;
import com.app.datadistribution.notification.dto.NotificationResponseDTO;
import com.app.datadistribution.notification.entity.Notification;

public interface INotificationService {

    Notification createNotification(NotificationCreateRequest request);

    NotificationPageResponseDTO getUserNotifications(UUID userId, boolean unreadOnly, Pageable pageable);

    NotificationCountDTO getNotificationCounts(UUID userId);

    NotificationResponseDTO markAsRead(UUID notificationId, UUID userId);

    void markAllAsRead(UUID userId);

    void deleteNotification(UUID notificationId, UUID userId);

    NotificationResponseDTO toDto(Notification notification);
}

