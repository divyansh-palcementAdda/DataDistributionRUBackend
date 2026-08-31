package com.app.datadistribution.entity;

import com.app.datadistribution.common.BaseEntity;
import com.app.datadistribution.enums.SentimentCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lead_statuses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadStatus extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment_category", nullable = false, length = 20)
    @Builder.Default
    private SentimentCategory sentimentCategory = SentimentCategory.NEUTRAL;

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "parent_status_id")
    private LeadStatus parentStatus;

    @Column(name = "is_sequential", nullable = false)
    @Builder.Default
    private boolean isSequential = false;

    @Column(name = "daily_attempt_limit")
    private Integer dailyAttemptLimit;

    @Column(name = "is_follow_up_status", nullable = false)
    @Builder.Default
    private boolean isFollowUpStatus = false;

    public String getStatus() {
        return active ? "ACTIVE" : "INACTIVE";
    }
}
