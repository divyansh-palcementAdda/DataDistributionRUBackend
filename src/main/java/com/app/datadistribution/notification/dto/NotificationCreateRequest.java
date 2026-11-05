package com.app.datadistribution.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.app.datadistribution.notification.enums.NotificationPriority;
import com.app.datadistribution.notification.enums.NotificationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCreateRequest {

    private UUID recipientId;
    private String title;
    private String message;
    private NotificationType type;
    private NotificationPriority priority;
    private String relatedEntityType;
    private String relatedEntityId;
    private String navigationUrl;
    private String metadata;
    private String idempotencyKey;
    private LocalDateTime expiresAt;
}

