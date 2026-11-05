package com.app.datadistribution.notification.service;

import com.app.datadistribution.entity.User;
import com.app.datadistribution.notification.dto.AdminNotificationResultDTO;
import com.app.datadistribution.notification.dto.NotificationResponseDTO;
import com.app.datadistribution.notification.dto.SendAdminNotificationRequest;

public interface INotificationAdminService {

    AdminNotificationResultDTO sendManualNotification(SendAdminNotificationRequest request, User admin);

    NotificationResponseDTO sendTestNotification(User admin);
}

