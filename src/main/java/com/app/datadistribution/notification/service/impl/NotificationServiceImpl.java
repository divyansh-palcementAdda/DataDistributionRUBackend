package com.app.datadistribution.notification.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.notification.dto.NotificationCountDTO;
import com.app.datadistribution.notification.dto.NotificationCreateRequest;
import com.app.datadistribution.notification.dto.NotificationPageResponseDTO;
import com.app.datadistribution.notification.dto.NotificationResponseDTO;
import com.app.datadistribution.notification.entity.Notification;
import com.app.datadistribution.notification.enums.NotificationPriority;
import com.app.datadistribution.notification.enums.NotificationType;
import com.app.datadistribution.notification.repository.NotificationRepository;
import com.app.datadistribution.notification.service.INotificationPreferenceService;
import com.app.datadistribution.notification.service.INotificationRealtimeService;
import com.app.datadistribution.notification.service.INotificationService;
import com.app.datadistribution.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements INotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final INotificationPreferenceService preferenceService;
    private final INotificationRealtimeService realtimeService;

    @Override
    @Transactional
    public Notification createNotification(NotificationCreateRequest request) {
        if (request == null || request.getRecipientId() == null) {
            log.warn("Cannot create notification: request or recipientId is null");
            return null;
        }

        // 1. Idempotency check: prevent duplicate notifications
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            if (notificationRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
                log.info("Duplicate notification ignored with idempotency key: {}", request.getIdempotencyKey());
                return null;
            }
        }

        // 2. Preference check: verify user has this notification type enabled
        NotificationType type = request.getType() != null ? request.getType() : NotificationType.SYSTEM;
        if (!preferenceService.isNotificationEnabled(request.getRecipientId(), type)) {
            log.info("Notification type {} skipped for user {} due to user preferences", type, request.getRecipientId());
            return null;
        }

        // 3. Resolve recipient user
        User recipient = userRepository.findById(request.getRecipientId())
                .filter(u -> !u.isDeleted())
                .orElse(null);

        if (recipient == null) {
            log.warn("Recipient user {} not found or deleted. Skipping notification.", request.getRecipientId());
            return null;
        }

        // 4. Build and persist notification
        Notification notification = Notification.builder()
                .recipient(recipient)
                .title(request.getTitle())
                .message(request.getMessage())
                .type(type)
                .priority(request.getPriority() != null ? request.getPriority() : NotificationPriority.NORMAL)
                .relatedEntityType(request.getRelatedEntityType())
                .relatedEntityId(request.getRelatedEntityId())
                .navigationUrl(request.getNavigationUrl())
                .metadata(request.getMetadata())
                .idempotencyKey(request.getIdempotencyKey())
                .expiresAt(request.getExpiresAt())
                .read(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Persisted in-app notification {} for recipient {}: '{}'", saved.getId(), recipient.getUsername(), saved.getTitle());

        // 5. Real-time push via SSE
        try {
            NotificationResponseDTO dto = toDto(saved);
            realtimeService.pushNotification(recipient.getId(), dto);
        } catch (Exception e) {
            log.warn("Failed to push real-time notification to user {}: {}", recipient.getId(), e.getMessage());
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageResponseDTO getUserNotifications(UUID userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadFalseAndIsDeletedFalse(userId, pageable)
                : notificationRepository.findByRecipientIdAndIsDeletedFalse(userId, pageable);

        List<NotificationResponseDTO> content = page.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        long unreadCount = notificationRepository.countUnreadByRecipientId(userId);

        return NotificationPageResponseDTO.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .unreadCount(unreadCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationCountDTO getNotificationCounts(UUID userId) {
        long unread = notificationRepository.countUnreadByRecipientId(userId);
        long total = notificationRepository.countTotalByRecipientId(userId);
        return NotificationCountDTO.builder()
                .unreadCount(unread)
                .totalCount(total)
                .build();
    }

    @Override
    @Transactional
    public NotificationResponseDTO markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndRecipientIdAndIsDeletedFalse(notificationId, userId)
                .orElseThrow(() -> new ResourcesNotFoundException("Notification not found with id: " + notificationId));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return toDto(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsReadForRecipient(userId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void deleteNotification(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndRecipientIdAndIsDeletedFalse(notificationId, userId)
                .orElseThrow(() -> new ResourcesNotFoundException("Notification not found with id: " + notificationId));

        notification.setDeleted(true);
        notificationRepository.save(notification);
    }

    @Override
    public NotificationResponseDTO toDto(Notification notification) {
        if (notification == null) return null;

        String recipientName = null;
        UUID recipientId = null;
        if (notification.getRecipient() != null) {
            recipientId = notification.getRecipient().getId();
            recipientName = (notification.getRecipient().getFirstName() != null ? notification.getRecipient().getFirstName() : "")
                    + " " + (notification.getRecipient().getLastName() != null ? notification.getRecipient().getLastName() : "");
            recipientName = recipientName.trim();
        }

        return NotificationResponseDTO.builder()
                .id(notification.getId())
                .recipientId(recipientId)
                .recipientName(recipientName)
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .priority(notification.getPriority())
                .relatedEntityType(notification.getRelatedEntityType())
                .relatedEntityId(notification.getRelatedEntityId())
                .navigationUrl(notification.getNavigationUrl())
                .read(notification.isRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .metadata(notification.getMetadata())
                .build();
    }
}

