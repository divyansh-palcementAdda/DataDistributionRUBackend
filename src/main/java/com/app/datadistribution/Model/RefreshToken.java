package com.app.datadistribution.Model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row = one active login session (one device / browser / phone).
 */
@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
        @Index(name = "idx_refresh_token", columnList = "token", unique = true),
        @Index(name = "idx_user_id", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "token_version", nullable = false)
    private Long tokenVersion;

    @Column(name = "is_revoked", nullable = false)
    private boolean isRevoked = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, unique = true, length = 512)
    private String refreshToken;

    @Column(nullable = false, length = 1024)
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