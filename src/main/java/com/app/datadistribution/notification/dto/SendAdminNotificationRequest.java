package com.app.datadistribution.notification.dto;

import java.util.List;
import java.util.UUID;

import com.app.datadistribution.notification.enums.NotificationPriority;
import com.app.datadistribution.notification.enums.NotificationType;

import jakarta.validation.constraints.NotBlank;
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
public class SendAdminNotificationRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Message is required")
    private String message;

    @Builder.Default
    private NotificationType type = NotificationType.ADMIN_ANNOUNCEMENT;

    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;

    /**
     * Target mode: USER, SELECTED_USERS, ROLE, ALL
     */
    @Builder.Default
    private String targetType = "ALL";

    private List<UUID> userIds;

    private String roleName;

    private String navigationUrl;
}

