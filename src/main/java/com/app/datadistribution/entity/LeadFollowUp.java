package com.app.datadistribution.entity;

import java.time.LocalDateTime;

import com.app.datadistribution.common.BaseEntity;
import com.app.datadistribution.enums.FollowUpStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "lead_follow_ups",
    indexes = {
        @Index(name = "idx_lfu_lead_status", columnList = "lead_id, status"),
        @Index(name = "idx_lfu_assigned_to", columnList = "assigned_to_user_id"),
        @Index(name = "idx_lfu_follow_up_date", columnList = "follow_up_date")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadFollowUp extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Column(name = "follow_up_date", nullable = false)
    private LocalDateTime followUpDate;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FollowUpStatus status;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_remarks", columnDefinition = "TEXT")
    private String cancellationRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedTo;
}
