package com.app.datadistribution.entity;

import com.app.datadistribution.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "lead_availed",
    indexes = {
        @Index(name = "idx_lead_availed_lead", columnList = "lead_id"),
        @Index(name = "idx_lead_availed_user", columnList = "availed_by_user_id"),
        @Index(name = "idx_lead_availed_at", columnList = "availed_at"),
        @Index(name = "idx_lead_availed_assignment", columnList = "assignment_history_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadAvailed extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "availed_by_user_id", nullable = false)
    private User availedByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_history_id")
    private LeadAssignmentHistory assignmentHistory;

    @Column(name = "availed_at", nullable = false)
    private LocalDateTime availedAt;
}
