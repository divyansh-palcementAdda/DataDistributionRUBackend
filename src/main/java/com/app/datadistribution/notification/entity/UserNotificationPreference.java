package com.app.datadistribution.notification.entity;

import java.util.UUID;

import com.app.datadistribution.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "user_notification_preferences",
    indexes = {
        @Index(name = "uk_unp_user_id", columnList = "user_id", unique = true)
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "follow_up_reminders_enabled", nullable = false)
    @Builder.Default
    private boolean followUpRemindersEnabled = true;

    @Column(name = "lead_allocation_enabled", nullable = false)
    @Builder.Default
    private boolean leadAllocationEnabled = true;

    @Column(name = "lead_reassignment_enabled", nullable = false)
    @Builder.Default
    private boolean leadReassignmentEnabled = true;

    @Column(name = "task_assignment_enabled", nullable = false)
    @Builder.Default
    private boolean taskAssignmentEnabled = true;

    @Column(name = "email_notifications_enabled", nullable = false)
    @Builder.Default
    private boolean emailNotificationsEnabled = true;

    @Column(name = "browser_notifications_enabled", nullable = false)
    @Builder.Default
    private boolean browserNotificationsEnabled = true;
}

