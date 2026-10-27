package com.app.datadistribution.integration.meta.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.app.datadistribution.common.BaseEntity;

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
@Table(name = "meta_webhook_events", indexes = {
    @Index(name = "idx_meta_leadgen_id", columnList = "leadgen_id", unique = true),
    @Index(name = "idx_meta_status_next_retry", columnList = "status, next_retry_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaWebhookEvent extends BaseEntity {

    @Column(name = "leadgen_id", nullable = false, unique = true, length = 64)
    private String leadgenId;

    @Column(name = "page_id", length = 64)
    private String pageId;

    @Column(name = "form_id", length = 64)
    private String formId;

    @Column(name = "ad_id", length = 64)
    private String adId;

    @Column(name = "adgroup_id", length = 64)
    private String adgroupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private MetaWebhookEventStatus status = MetaWebhookEventStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 5;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "raw_payload", columnDefinition = "LONGTEXT")
    private String rawPayload;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "lead_code", length = 50)
    private String leadCode;
}
