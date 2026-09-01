package com.app.datadistribution.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.app.datadistribution.common.BaseEntity;
import com.app.datadistribution.enums.EmailStatus;
import com.app.datadistribution.enums.EmailType;

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
    name = "email_logs",
    indexes = {
        @Index(name = "idx_el_idempotency_key", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_el_recipient_user_id", columnList = "recipient_user_id"),
        @Index(name = "idx_el_email_type", columnList = "email_type"),
        @Index(name = "idx_el_status", columnList = "status"),
        @Index(name = "idx_el_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLog extends BaseEntity {

    @Column(name = "idempotency_key", length = 200, unique = true)
    private String idempotencyKey;

    @Column(name = "recipient_email", nullable = false, length = 150)
    private String recipientEmail;

    @Column(name = "recipient_name", length = 150)
    private String recipientName;

    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 50)
    private EmailType emailType;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EmailStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;
}
