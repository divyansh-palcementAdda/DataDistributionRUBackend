package com.app.datadistribution.notification.service.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.notification.enums.NotificationPriority;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.notification.dto.AdminNotificationResultDTO;
import com.app.datadistribution.notification.dto.NotificationCreateRequest;
import com.app.datadistribution.notification.dto.NotificationResponseDTO;
import com.app.datadistribution.notification.dto.SendAdminNotificationRequest;
import com.app.datadistribution.notification.entity.Notification;
import com.app.datadistribution.notification.enums.NotificationType;
import com.app.datadistribution.notification.service.INotificationAdminService;
import com.app.datadistribution.notification.service.INotificationService;
import com.app.datadistribution.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationAdminServiceImpl implements INotificationAdminService {

    private final UserRepository userRepository;
    private final INotificationService notificationService;

    @Override
    @Transactional
    public AdminNotificationResultDTO sendManualNotification(SendAdminNotificationRequest request, User admin) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        Set<User> recipients = resolveRecipients(request);
        if (recipients.isEmpty()) {
            return AdminNotificationResultDTO.builder()
                    .totalTargeted(0)
                    .successCount(0)
                    .failureCount(0)
                    .statusMessage("No eligible active recipients found for the specified target")
                    .build();
        }

        int success = 0;
        int failure = 0;
        String batchId = UUID.randomUUID().toString();

        for (User recipient : recipients) {
            try {
                String idempotencyKey = "ADMIN_NOTIF:" + batchId + ":" + recipient.getId();
                NotificationCreateRequest notifRequest = NotificationCreateRequest.builder()
                        .recipientId(recipient.getId())
                        .title(request.getTitle())
                        .message(request.getMessage())
                        .type(request.getType() != null ? request.getType() : NotificationType.ADMIN_ANNOUNCEMENT)
                        .priority(request.getPriority() != null ? request.getPriority() : com.app.datadistribution.notification.enums.NotificationPriority.NORMAL)
                        .navigationUrl(request.getNavigationUrl())
                        .idempotencyKey(idempotencyKey)
                        .metadata("{\"sentByAdmin\":\"" + (admin != null ? admin.getUsername() : "SYSTEM") + "\"}")
                        .build();

                Notification created = notificationService.createNotification(notifRequest);
                if (created != null) {
                    success++;
                }
            } catch (Exception e) {
                log.error("Failed to send admin notification to user {}: {}", recipient.getUsername(), e.getMessage());
                failure++;
            }
        }

        log.info("Admin {} sent manual notification '{}' to {} users (success: {}, failure: {})",
                admin != null ? admin.getUsername() : "UNKNOWN", request.getTitle(), recipients.size(), success, failure);

        return AdminNotificationResultDTO.builder()
                .totalTargeted(recipients.size())
                .successCount(success)
                .failureCount(failure)
                .statusMessage("Successfully sent notification to " + success + " of " + recipients.size() + " recipient(s)")
                .build();
    }

    @Override
    @Transactional
    public NotificationResponseDTO sendTestNotification(User admin) {
        if (admin == null || admin.getId() == null) {
            throw new IllegalArgumentException("Admin user cannot be null");
        }

        String idempotencyKey = "TEST_NOTIF:" + admin.getId() + ":" + System.currentTimeMillis();
        NotificationCreateRequest notifRequest = NotificationCreateRequest.builder()
                .recipientId(admin.getId())
                .title("Test Notification")
                .message("Your DDS in-app notification system is working correctly. Real-time delivery and unread counting verified.")
                .type(NotificationType.TEST)
                .priority(com.app.datadistribution.notification.enums.NotificationPriority.NORMAL)
                .navigationUrl("/notifications")
                .idempotencyKey(idempotencyKey)
                .metadata("{\"isTest\":true}")
                .build();

        Notification created = notificationService.createNotification(notifRequest);
        return notificationService.toDto(created);
    }

    private Set<User> resolveRecipients(SendAdminNotificationRequest request) {
        String targetType = request.getTargetType() != null ? request.getTargetType().toUpperCase() : "ALL";

        return switch (targetType) {
            case "USER", "SELECTED_USERS" -> {
                if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
                    yield Collections.emptySet();
                }
                yield new HashSet<>(userRepository.findAllById(request.getUserIds()).stream()
                        .filter(u -> !u.isDeleted() && u.isActive())
                        .collect(Collectors.toList()));
            }
            case "ROLE" -> {
                if (request.getRoleName() == null || request.getRoleName().isBlank()) {
                    yield Collections.emptySet();
                }
                String roleSearch = request.getRoleName().trim().toUpperCase();
                yield userRepository.findAll().stream()
                        .filter(u -> !u.isDeleted() && u.isActive() && u.getRoles() != null)
                        .filter(u -> u.getRoles().stream().anyMatch(r ->
                                r.getName() != null && (
                                        r.getName().equalsIgnoreCase(roleSearch) ||
                                        r.getName().equalsIgnoreCase("ROLE_" + roleSearch) ||
                                        ("ROLE_" + r.getName()).equalsIgnoreCase(roleSearch)
                                )))
                        .collect(Collectors.toSet());
            }
            case "ALL" -> userRepository.findAll().stream()
                    .filter(u -> !u.isDeleted() && u.isActive())
                    .collect(Collectors.toSet());
            default -> Collections.emptySet();
        };
    }
}
