package com.app.datadistribution.notification.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.app.datadistribution.common.BaseEntity;
import com.app.datadistribution.notification.enums.ReminderStatus;
import com.app.datadistribution.notification.enums.ReminderType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "follow_up_reminders",
    indexes = {
        @Index(name = "idx_fur_due_status", columnList = "status, scheduled_for"),
        @Index(name = "idx_fur_followup_status", columnList = "follow_up_id, status"),
        @Index(name = "idx_fur_recipient", columnList = "recipient_user_id"),
        @Index(name = "idx_fur_idempotency", columnList = "idempotency_key", unique = true)
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpReminder extends BaseEntity {

    @Column(name = "follow_up_id", nullable = false)
    private UUID followUpId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 20)
    private ReminderType reminderType;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDateTime scheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReminderStatus status = ReminderStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "claimed_by", length = 100)
    private String claimedBy;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "idempotency_key", length = 150, nullable = false, unique = true)
    private String idempotencyKey;
}

