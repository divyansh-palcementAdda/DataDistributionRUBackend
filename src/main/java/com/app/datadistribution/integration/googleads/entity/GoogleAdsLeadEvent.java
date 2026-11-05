package com.app.datadistribution.integration.googleads.entity;

import com.app.datadistribution.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
    name = "google_ads_lead_events",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_google_ads_provider_ext_id",
            columnNames = {"provider", "external_lead_id"}
        )
    },
    indexes = {
        @Index(name = "idx_google_ads_ext_id", columnList = "external_lead_id"),
        @Index(name = "idx_google_ads_status", columnList = "processing_status"),
        @Index(name = "idx_google_ads_is_test", columnList = "is_test"),
        @Index(name = "idx_google_ads_lead_id", columnList = "linked_lead_id"),
        @Index(name = "idx_google_ads_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAdsLeadEvent extends BaseEntity {

    @Column(name = "provider", nullable = false, length = 50)
    @Builder.Default
    private String provider = "GOOGLE_ADS";

    @Column(name = "external_lead_id", nullable = false, length = 500)
    private String externalLeadId;

    @Column(name = "form_id", length = 255)
    private String formId;

    @Column(name = "campaign_id", length = 255)
    private String campaignId;

    @Column(name = "adgroup_id", length = 255)
    private String adgroupId;

    @Column(name = "creative_id", length = 255)
    private String creativeId;

    @Column(name = "gcl_id", length = 500)
    private String gclId;

    @Column(name = "lead_source", length = 255)
    private String leadSource;

    @Column(name = "submission_time", length = 255)
    private String submissionTime;

    @Column(name = "is_test", nullable = false)
    @Builder.Default
    private boolean isTest = false;

    @Column(name = "processing_status", nullable = false, length = 50)
    private String processingStatus;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "linked_lead_id")
    private UUID linkedLeadId;

    @Column(name = "lead_code", length = 50)
    private String leadCode;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "mobile", length = 50)
    private String mobile;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "course_interested", length = 255)
    private String courseInterested;
}

