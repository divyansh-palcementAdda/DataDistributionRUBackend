package com.app.datadistribution.entity;

import java.time.LocalDateTime;

import com.app.datadistribution.common.BaseEntity;
import com.app.datadistribution.enums.ActivityPeriodType;

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
    name = "user_activity_periods",
    indexes = {
        @Index(name = "idx_uap_session_id", columnList = "session_id"),
        @Index(name = "idx_uap_user_started", columnList = "user_id, started_at"),
        @Index(name = "idx_uap_period_type", columnList = "period_type")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityPeriod extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private UserLoginSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private ActivityPeriodType periodType;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(length = 255)
    private String reason;
}
