package com.app.datadistribution.notification.service;

import java.util.UUID;

import com.app.datadistribution.notification.dto.NotificationPreferenceDTO;
import com.app.datadistribution.notification.enums.NotificationType;

public interface INotificationPreferenceService {

    NotificationPreferenceDTO getPreferences(UUID userId);

    NotificationPreferenceDTO updatePreferences(UUID userId, NotificationPreferenceDTO dto);

    boolean isNotificationEnabled(UUID userId, NotificationType type);
}

