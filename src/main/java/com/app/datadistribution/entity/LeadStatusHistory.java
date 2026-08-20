package com.app.datadistribution.entity;

import com.app.datadistribution.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "lead_status_histories",
    indexes = {
        @Index(name = "idx_lsh_lead_id", columnList = "lead_id"),
        @Index(name = "idx_lsh_created_at", columnList = "created_at"),
        @Index(name = "idx_lsh_changed_by_user_id", columnList = "changed_by_user_id"),
        @Index(name = "idx_lsh_new_status_id", columnList = "new_status_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_status_id")
    private LeadStatus previousStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_status_id")
    private LeadStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private User changedByUser;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String feedback;

    public LeadStatus getOldStatus() {
        return previousStatus;
    }

    public void setOldStatus(LeadStatus oldStatus) {
        this.previousStatus = oldStatus;
    }
}
