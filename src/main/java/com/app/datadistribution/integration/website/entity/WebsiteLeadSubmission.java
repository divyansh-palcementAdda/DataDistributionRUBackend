package com.app.datadistribution.integration.website.entity;

import com.app.datadistribution.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "website_lead_submissions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_website_sub_ext_id_src",
            columnNames = {"external_lead_id", "source_code"}
        )
    },
    indexes = {
        @Index(name = "idx_website_sub_ext_id", columnList = "external_lead_id"),
        @Index(name = "idx_website_sub_source", columnList = "source_code"),
        @Index(name = "idx_website_sub_lead_id", columnList = "lead_id"),
        @Index(name = "idx_website_sub_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteLeadSubmission extends BaseEntity {

    @Column(name = "external_lead_id", length = 100)
    private String externalLeadId;

    @Column(name = "source_code", nullable = false, length = 50)
    private String sourceCode;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "lead_code", length = 50)
    private String leadCode;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "course_interested", length = 150)
    private String courseInterested;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "request_summary", columnDefinition = "TEXT")
    private String requestSummary;
}
