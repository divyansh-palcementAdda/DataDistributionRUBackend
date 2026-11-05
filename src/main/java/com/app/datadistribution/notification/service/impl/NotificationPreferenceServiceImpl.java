package com.app.datadistribution.notification.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.notification.dto.NotificationPreferenceDTO;
import com.app.datadistribution.notification.entity.UserNotificationPreference;
import com.app.datadistribution.notification.enums.NotificationType;
import com.app.datadistribution.notification.repository.UserNotificationPreferenceRepository;
import com.app.datadistribution.notification.service.INotificationPreferenceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceServiceImpl implements INotificationPreferenceService {

    private final UserNotificationPreferenceRepository preferenceRepository;

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferenceDTO getPreferences(UUID userId) {
        if (userId == null) {
            return buildDefaultPreferences(null);
        }

        UserNotificationPreference pref = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> buildDefaultEntity(userId));

        return toDto(pref);
    }

    @Override
    @Transactional
    public NotificationPreferenceDTO updatePreferences(UUID userId, NotificationPreferenceDTO dto) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        UserNotificationPreference pref = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> buildDefaultEntity(userId));

        pref.setFollowUpRemindersEnabled(dto.isFollowUpRemindersEnabled());
        pref.setLeadAllocationEnabled(dto.isLeadAllocationEnabled());
        pref.setLeadReassignmentEnabled(dto.isLeadReassignmentEnabled());
        pref.setTaskAssignmentEnabled(dto.isTaskAssignmentEnabled());
        pref.setEmailNotificationsEnabled(dto.isEmailNotificationsEnabled());
        pref.setBrowserNotificationsEnabled(dto.isBrowserNotificationsEnabled());

        UserNotificationPreference saved = preferenceRepository.save(pref);
        log.info("Updated notification preferences for user {}", userId);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isNotificationEnabled(UUID userId, NotificationType type) {
        if (userId == null || type == null) {
            return true;
        }

        // System, Admin Announcements, and Test are mandatory and cannot be disabled
        if (type == NotificationType.SYSTEM || type == NotificationType.ADMIN_ANNOUNCEMENT || type == NotificationType.TEST) {
            return true;
        }

        UserNotificationPreference pref = preferenceRepository.findByUserId(userId).orElse(null);
        if (pref == null) {
            return true; // default all enabled
        }

        return switch (type) {
            case FOLLOW_UP_REMINDER -> pref.isFollowUpRemindersEnabled();
            case NEW_LEAD_ASSIGNED -> pref.isLeadAllocationEnabled();
            case LEAD_REASSIGNED -> pref.isLeadReassignmentEnabled();
            case TASK_ASSIGNED -> pref.isTaskAssignmentEnabled();
            default -> true;
        };
    }

    private UserNotificationPreference buildDefaultEntity(UUID userId) {
        return UserNotificationPreference.builder()
                .userId(userId)
                .followUpRemindersEnabled(true)
                .leadAllocationEnabled(true)
                .leadReassignmentEnabled(true)
                .taskAssignmentEnabled(true)
                .emailNotificationsEnabled(true)
                .browserNotificationsEnabled(true)
                .build();
    }

    private NotificationPreferenceDTO buildDefaultPreferences(UUID userId) {
        return NotificationPreferenceDTO.builder()
                .userId(userId)
                .followUpRemindersEnabled(true)
                .leadAllocationEnabled(true)
                .leadReassignmentEnabled(true)
                .taskAssignmentEnabled(true)
                .emailNotificationsEnabled(true)
                .browserNotificationsEnabled(true)
                .build();
    }

    private NotificationPreferenceDTO toDto(UserNotificationPreference entity) {
        return NotificationPreferenceDTO.builder()
                .userId(entity.getUserId())
                .followUpRemindersEnabled(entity.isFollowUpRemindersEnabled())
                .leadAllocationEnabled(entity.isLeadAllocationEnabled())
                .leadReassignmentEnabled(entity.isLeadReassignmentEnabled())
                .taskAssignmentEnabled(entity.isTaskAssignmentEnabled())
                .emailNotificationsEnabled(entity.isEmailNotificationsEnabled())
                .browserNotificationsEnabled(entity.isBrowserNotificationsEnabled())
                .build();
    }
}

