package com.app.datadistribution.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.app.datadistribution.notification.enums.NotificationPriority;
import com.app.datadistribution.notification.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonFormat;

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
public class NotificationResponseDTO {

    private UUID id;
    private UUID recipientId;
    private String recipientName;
    private String title;
    private String message;
    private NotificationType type;
    private NotificationPriority priority;
    private String relatedEntityType;
    private String relatedEntityId;
    private String navigationUrl;
    private boolean read;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private String metadata;
}

