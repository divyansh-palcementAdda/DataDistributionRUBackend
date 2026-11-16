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
    @Index(name = "idx_meta_status_next_retry", columnList = "status, next_retry_at"),
    @Index(name = "idx_meta_campaign_id", columnList = "campaign_id")
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

    @Column(name = "ad_name", length = 255)
    private String adName;

    @Column(name = "adgroup_id", length = 64)
    private String adgroupId;

    @Column(name = "adset_name", length = 255)
    private String adsetName;

    @Column(name = "campaign_id", length = 64)
    private String campaignId;

    @Column(name = "campaign_name", length = 255)
    private String campaignName;

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

    @Column(name = "course_question", length = 255)
    private String courseQuestion;

    @Column(name = "course_answer", length = 255)
    private String courseAnswer;

    @Column(name = "resolved_course_id")
    private UUID resolvedCourseId;

    @Column(name = "mapping_method", length = 50)
    private String mappingMethod;

    @Column(name = "mapping_reason", length = 255)
    private String mappingReason;
}
