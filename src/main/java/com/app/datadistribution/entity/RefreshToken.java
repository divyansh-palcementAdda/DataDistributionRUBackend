package com.app.datadistribution.entity;

import com.app.datadistribution.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
        @Index(name = "idx_refresh_token", columnList = "refresh_token", unique = true),
        @Index(name = "idx_user_id", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "token_version", nullable = false)
    private Long tokenVersion;

    @Column(name = "is_revoked", nullable = false)
    @Builder.Default
    private boolean isRevoked = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "refresh_token", nullable = false, unique = true, length = 512)
    private String refreshToken;

    @Column(name = "access_token", nullable = false, length = 1024)
    private String accessToken;

    @Column(name = "client_ip", nullable = false, length = 45)
    private String clientIp;

    @Column(name = "device_info", length = 512)
    private String deviceInfo;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Column(name = "access_expiry", nullable = false)
    private Instant accessTokenExpiry;

    @Column(name = "last_used")
    private Instant lastUsed;

    @Transient
    public boolean isRefreshExpired() {
        return Instant.now().isAfter(expiryDate);
    }

    @Transient
    public boolean isAccessExpired() {
        return Instant.now().isAfter(accessTokenExpiry);
    }
}
