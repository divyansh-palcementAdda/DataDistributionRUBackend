package com.app.datadistribution.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.app.datadistribution.entity.RefreshToken;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.entity.UserDevice;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.InvalidRefreshTokenException;
import com.app.datadistribution.repository.RefreshTokenRepository;
import com.app.datadistribution.repository.UserDeviceRepository;
import com.app.datadistribution.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final UserDeviceRepository userDeviceRepository;

    @Value("${jwt.access.expiration-ms:900000}")
    private long accessTokenDurationMs;

    @Value("${jwt.refresh.expiration-ms:604800000}")
    private long refreshTokenDurationMs;

    @Transactional
    public RefreshToken createRefreshToken(User user, String accessToken, String clientIp, String deviceInfo) throws AccessDeniedException {
        validateUser(user);

        String refreshTokenString = jwtService.generateRefreshToken(
            user.getUsername(),
            Map.of("userId", user.getId(), "tokenVersion", user.getTokenVersion())
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

        UserDevice device = UserDevice.builder()
                .userId(user.getId())
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
        return refreshTokenRepository.save(saved);
    }

    public static String getBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg")) return "Edge";
        if (ua.contains("chrome") && !ua.contains("chromium")) return "Chrome";
        if (ua.contains("safari") && !ua.contains("chrome")) return "Safari";
        if (ua.contains("firefox")) return "Firefox";
        if (ua.contains("opera") || ua.contains("opr")) return "Opera";
        return "Browser";
    }

    public static String getOs(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("macintosh") || ua.contains("mac os x")) return "macOS";
        if (ua.contains("iphone") || ua.contains("ipad")) return "iOS";
        if (ua.contains("android")) return "Android";
        if (ua.contains("linux")) return "Linux";
        return "OS";
    }

    public static String getDevice(String userAgent) {
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

    public RefreshToken validateAndGetRefreshToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new InvalidRefreshTokenException("Refresh token is required");
        }
        RefreshToken rt = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token does not exist"));

        if (rt.isRefreshExpired() || rt.getExpiryDate().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        if (rt.isRevoked()) {
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }

        if (rt.getDeviceId() != null) {
            UserDevice device = userDeviceRepository.findById(rt.getDeviceId())
                    .orElseThrow(() -> new InvalidRefreshTokenException("Device associated with token not found"));
            if (!device.isActive()) {
                throw new InvalidRefreshTokenException("Device associated with token is inactive");
            }
        }

        User user = rt.getUser();
        if (user == null || !user.isActive() || !user.isEmailVerified()) {
            throw new InvalidRefreshTokenException("User account is no longer active or email not verified");
        }

        if (rt.getTokenVersion() == null || !rt.getTokenVersion().equals(user.getTokenVersion())) {
            throw new InvalidRefreshTokenException("Refresh token version mismatch");
        }

        return rt;
    }

    @Transactional
    public RefreshToken refreshAccessToken(String refreshTokenStr) {
        RefreshToken rt = validateAndGetRefreshToken(refreshTokenStr);
        return verifyAndRefresh(rt);
    }

    public boolean isAccessTokenValidForUser(UUID userId, String accessToken) {
        return refreshTokenRepository.findByUser_IdAndAccessToken(userId, accessToken)
                .map(RefreshToken::isAccessExpired)
                .map(expired -> !expired)
                .orElse(false);
    }

    @Transactional
    public void revokeByRefreshToken(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(rt -> {
            log.info("Revoking session for user {} from {}", rt.getUser().getId(), rt.getClientIp());
            
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            
            if (rt.getDeviceId() != null) {
                userDeviceRepository.findById(rt.getDeviceId()).ifPresent(device -> {
                    device.setActive(false);
                    device.setLogoutAt(Instant.now());
                    device.setRefreshTokenId(null);
                    userDeviceRepository.save(device);
                });
            }
        });
    }

    @Transactional
    public void revokeAllByUserId(UUID userId) {
        if (userId == null) return;

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
        if (user == null || !user.isActive() || !user.isEmailVerified()) {
            throw new AccessDeniedException("Cannot create session – account inactive or unverified");
        }
    }

    @Transactional
    public RefreshToken verifyAndRefresh(RefreshToken rt) {
        RefreshToken validated = validateAndGetRefreshToken(rt.getRefreshToken());
        User user = validated.getUser();

        Map<String, Object> claims = Map.of(
            "userId", user.getId().toString(),
            "tokenVersion", user.getTokenVersion()
        );
        String newAccessToken = jwtService.generateAccessToken(user.getUsername(), claims);

        validated.setAccessToken(newAccessToken);
        validated.setAccessTokenExpiry(Instant.now().plusMillis(accessTokenDurationMs));
        validated.setLastUsed(Instant.now());

        RefreshToken saved = refreshTokenRepository.save(validated);

        userDeviceRepository.findByRefreshTokenId(saved.getId()).ifPresent(device -> {
            device.setLastActivityAt(Instant.now());
            userDeviceRepository.save(device);
        });

        log.info("TOKEN ROTATION | User: {} | New Access Expiry: {} | Refresh Expiry: {}", 
                  user.getUsername(), saved.getAccessTokenExpiry(), saved.getExpiryDate());

        return saved;
    }
}