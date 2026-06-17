// RefreshTokenService.java  (updated with isAccessTokenValidForUser helper)
package com.app.datadistribution.service.impl;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.app.datadistribution.Model.RefreshToken;
import com.app.datadistribution.Model.User;
import com.app.datadistribution.Model.UserDevice;
import com.app.datadistribution.Model.UserStatus;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.repository.RefreshTokenRepository;
import com.app.datadistribution.repository.UserDeviceRepository;
import com.app.datadistribution.security.JwtProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final UserDeviceRepository userDeviceRepository;

    @Value("${jwt.access.expiration-ms:900000}")
    private long accessTokenDurationMs;

    @Value("${jwt.refresh.expiration-ms:604800000}")
    private long refreshTokenDurationMs;

    @Transactional
    public RefreshToken createRefreshToken(User user, String accessToken, String clientIp, String deviceInfo) throws AccessDeniedException {
        validateUser(user);

        String refreshTokenString = jwtProvider.generateRefreshToken(
            user.getUsername(),
            Map.of("userId", user.getUserId(), "tokenVersion", user.getTokenVersion())
        );

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .refreshToken(refreshTokenString)
                .accessToken(accessToken)
                .clientIp(clientIp)
                .deviceInfo(deviceInfo != null && deviceInfo.length() > 512 ? deviceInfo.substring(0, 512) : deviceInfo)
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .expiresAt(Instant.now().plusMillis(refreshTokenDurationMs))
                .accessTokenExpiry(Instant.now().plusMillis(accessTokenDurationMs))
                .lastUsed(Instant.now())
                .tokenVersion(user.getTokenVersion())
                .isRevoked(false)
                .build();

        RefreshToken saved = refreshTokenRepository.save(token);

        // Create active user device session record
        UserDevice device = UserDevice.builder()
                .userId(user.getUserId())
                .refreshTokenId(saved.getId())
                .deviceName(getDevice(deviceInfo))
                .browser(getBrowser(deviceInfo))
                .os(getOs(deviceInfo))
                .ipAddress(clientIp)
                .userAgent(deviceInfo != null && deviceInfo.length() > 512 ? deviceInfo.substring(0, 512) : deviceInfo)
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .isActive(true)
                .build();
        UserDevice savedDevice = userDeviceRepository.save(device);

        saved.setDeviceId(savedDevice.getId());
        refreshTokenRepository.save(saved);

        return saved;
    }

    private String getBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg")) return "Edge";
        if (ua.contains("chrome") && !ua.contains("chromium")) return "Chrome";
        if (ua.contains("safari") && !ua.contains("chrome")) return "Safari";
        if (ua.contains("firefox")) return "Firefox";
        if (ua.contains("opera") || ua.contains("opr")) return "Opera";
        return "Browser";
    }

    private String getOs(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("macintosh") || ua.contains("mac os x")) return "macOS";
        if (ua.contains("iphone") || ua.contains("ipad")) return "iOS";
        if (ua.contains("android")) return "Android";
        if (ua.contains("linux")) return "Linux";
        return "OS";
    }

    private String getDevice(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("iphone")) return "iPhone";
        if (ua.contains("ipad")) return "iPad";
        if (ua.contains("android")) {
            if (ua.contains("mobile")) return "Android Mobile";
            return "Android Tablet";
        }
        if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("linux")) return "Desktop";
        return "Device";
    }

    public Optional<RefreshToken> findByRefreshToken(String token) {
        if (!StringUtils.hasText(token)) return Optional.empty();
        return refreshTokenRepository.findByRefreshToken(token);
    }

    @Transactional
    public RefreshToken refreshAccessToken(String refreshTokenStr) throws AccessDeniedException {
        RefreshToken rt = findByRefreshToken(refreshTokenStr)
                .orElseThrow(() -> new AccessDeniedException("Invalid refresh token"));

        if (rt.isRefreshExpired()) {
            refreshTokenRepository.delete(rt);
            throw new AccessDeniedException("Refresh token has expired");
        }

        User user = rt.getUser();
        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            throw new AccessDeniedException("Account not active or not verified");
        }

        String newAccessToken = jwtProvider.generateAccessToken(
                user.getUsername(),
                Map.of("userId", user.getUserId())
        );

        rt.setAccessToken(newAccessToken);
        rt.setAccessTokenExpiry(Instant.now().plusMillis(accessTokenDurationMs));
        rt.setLastUsed(Instant.now());

        return refreshTokenRepository.save(rt);
    }

    public boolean isAccessTokenValidForUser(Long userId, String accessToken) {
        return refreshTokenRepository.findByUser_UserIdAndAccessToken(userId, accessToken)
                .map(RefreshToken::isAccessExpired)
                .map(expired -> !expired)
                .orElse(false);
    }

    @Transactional
    public void revokeByRefreshToken(String refreshToken) {
        findByRefreshToken(refreshToken).ifPresent(rt -> {
            log.info("Revoking session for user {} from {}", rt.getUser().getUserId(), rt.getClientIp());
            
            // Mark UserDevice as inactive
            userDeviceRepository.findByRefreshTokenId(rt.getId()).ifPresent(device -> {
                device.setActive(false);
                device.setLogoutAt(Instant.now());
                device.setRefreshTokenId(null);
                userDeviceRepository.save(device);
            });

            refreshTokenRepository.delete(rt);
        });
    }

    @Transactional
    public void revokeAllByUserId(Long userId) {
        if (userId == null) return;

        // Mark all devices for this user as inactive
        List<UserDevice> activeDevices = userDeviceRepository.findByUserIdAndIsActiveTrue(userId);
        for (UserDevice device : activeDevices) {
            device.setActive(false);
            device.setLogoutAt(Instant.now());
            device.setRefreshTokenId(null);
            userDeviceRepository.save(device);
        }

        int count = refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Revoked {} sessions for user {}", count, userId);
    }

    private void validateUser(User user) throws AccessDeniedException {
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            throw new AccessDeniedException("Cannot create session – account inactive or unverified");
        }
    }

    /**
     * Verifies that the refresh token is still valid (not expired, user still active/verified)
     * and performs access token rotation if everything is okay.
     * 
     * @param rt the RefreshToken entity loaded from database
     * @return the updated RefreshToken entity (with new access token & updated expiry)
     * @throws AccessDeniedException if refresh token is expired, revoked, or user is invalid
     */
    @Transactional
    public RefreshToken verifyAndRefresh(RefreshToken rt) throws AccessDeniedException {
        // 1. Basic null & existence check (should already be handled by caller, but defensive)
        if (rt == null) {
            throw new AccessDeniedException("Refresh token not found");
        }

        // 2. Check refresh token expiry
        if (rt.isRefreshExpired()) {
            // Optional: auto-cleanup
            refreshTokenRepository.delete(rt);
            throw new AccessDeniedException("Refresh token has expired");
        }

        // 2.1 Check if refresh token was revoked
        if (rt.isRevoked()) {
            refreshTokenRepository.delete(rt);
            throw new AccessDeniedException("Refresh token has been revoked");
        }

        // 3. Check if user is still in good standing
        User user = rt.getUser();
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            // In strict systems you might revoke all tokens here
            refreshTokenRepository.delete(rt);
            throw new AccessDeniedException("User account is no longer active or email not verified");
        }

        // 3.1 Check if token version mismatch
        if (rt.getTokenVersion() == null || !rt.getTokenVersion().equals(user.getTokenVersion())) {
            refreshTokenRepository.delete(rt);
            throw new AccessDeniedException("Refresh token version mismatch");
        }

        // 4. (Optional but recommended) Check if access token was already compromised/rotated too many times
        // You could add a rotation counter or last-used check here if paranoid

        // 5. Generate brand new access token
        Map<String, Object> claims = Map.of(
            "userId", user.getUserId(),
            "tokenVersion", user.getTokenVersion()
        );
        String newAccessToken = jwtProvider.generateAccessToken(user.getUsername(), claims);

        // 6. Update entity
        rt.setAccessToken(newAccessToken);
        rt.setAccessTokenExpiry(Instant.now().plusMillis(accessTokenDurationMs));
        rt.setLastUsed(Instant.now());

        // 7. Persist changes
        RefreshToken saved = refreshTokenRepository.save(rt);

        // Update active device last activity
        userDeviceRepository.findByRefreshTokenId(saved.getId()).ifPresent(device -> {
            device.setLastActivityAt(Instant.now());
            userDeviceRepository.save(device);
        });

        log.info("TOKEN ROTATION | User: {} | New Access Expiry: {} | Refresh Expiry: {}", 
                  user.getUsername(), saved.getAccessTokenExpiry(), saved.getExpiryDate());

        return saved;
    }
}