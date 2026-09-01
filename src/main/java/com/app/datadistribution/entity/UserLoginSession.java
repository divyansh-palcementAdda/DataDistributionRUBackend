package com.app.datadistribution.entity;

import java.time.LocalDateTime;

import com.app.datadistribution.common.BaseEntity;
import com.app.datadistribution.enums.LogoutReason;
import com.app.datadistribution.enums.SessionStatus;

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
    name = "user_login_sessions",
    indexes = {
        @Index(name = "idx_uls_user_login", columnList = "user_id, login_at"),
        @Index(name = "idx_uls_user_status", columnList = "user_id, session_status"),
        @Index(name = "idx_uls_last_activity", columnList = "last_activity_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    @Column(name = "logout_at")
    private LocalDateTime logoutAt;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 30)
    @Builder.Default
    private SessionStatus sessionStatus = SessionStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "logout_reason", length = 50)
    private LogoutReason logoutReason;

    @Column(name = "total_active_duration_seconds", nullable = false)
    @Builder.Default
    private long totalActiveDurationSeconds = 0L;

    @Column(name = "total_inactive_duration_seconds", nullable = false)
    @Builder.Default
    private long totalInactiveDurationSeconds = 0L;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_info", length = 512)
    private String deviceInfo;

    @Column(name = "token_version")
    private Long tokenVersion;
}
