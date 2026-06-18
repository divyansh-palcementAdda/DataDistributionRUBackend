package com.app.datadistribution.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.app.datadistribution.dto.auth.LoginRequest;
import com.app.datadistribution.dto.auth.LoginResponse;
import com.app.datadistribution.dto.auth.RegisterRequest;
import com.app.datadistribution.dto.auth.TokenResponse;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.entity.RefreshToken;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.ActivityType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.AuthenticationFailedException;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.DuplicateResourceException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.UserMapper;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.security.JwtService;
import com.app.datadistribution.security.UserDetailsImpl;
import com.app.datadistribution.service.interfaces.IActivityLogService;
import com.app.datadistribution.service.interfaces.IAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final RefreshTokenService refreshTokenService;
    private final IActivityLogService activityLogService;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest loginRequest, HttpServletRequest httpRequest)
            throws AuthenticationFailedException, AccessDeniedException {
        
        String identifier = loginRequest.getEmailOrUsername().trim().toLowerCase();
        String clientIp = extractClientIp(httpRequest);
        String deviceInfo = httpRequest.getHeader("User-Agent");

        log.info("LOGIN ATTEMPT | Identifier: {} | IP: {}", identifier, clientIp);

        User user = userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(identifier, identifier)
                .orElseThrow(() -> {
                    log.warn("LOGIN FAILED | Invalid credentials for: {}", identifier);
                    activityLogService.logActivity(ActivityType.LOGIN_FAILED,
                            "Failed login attempt for " + identifier, clientIp);
                    return new AuthenticationFailedException("Invalid credentials");
                });

        if (!user.isActive()) {
            log.warn("LOGIN BLOCKED | Account inactive: {}", user.getEmail());
            activityLogService.logActivity(ActivityType.LOGIN_FAILED, "Login blocked - account inactive", user.getEmail());
            throw new AccessDeniedException("Account is inactive");
        }

        if (user.isLocked()) {
            log.warn("LOGIN BLOCKED | Account locked: {}", user.getEmail());
            activityLogService.logActivity(ActivityType.LOGIN_FAILED, "Login blocked - account locked", user.getEmail());
            throw new AccessDeniedException("Account is locked");
        }

        if (!user.isEmailVerified()) {
            log.warn("LOGIN BLOCKED | Email not verified: {}", user.getEmail());
            activityLogService.logActivity(ActivityType.LOGIN_FAILED, "Login blocked - email not verified", user.getEmail());
            throw new AuthenticationFailedException("Email is not verified");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        Map<String, Object> claims = Map.of(
            "userId", userDetails.getId().toString(),
            "tokenVersion", userDetails.getTokenVersion()
        );
        String accessToken = jwtService.generateAccessToken(userDetails.getUsername(), claims);

        RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(
                user, accessToken, clientIp, deviceInfo);

        log.info("LOGIN SUCCESS | User: {} | ID: {} | IP: {}", user.getEmail(), user.getId(), clientIp);

        activityLogService.logActivity(
                ActivityType.LOGIN,
                String.format("Login successful. IP: %s, Browser: %s, OS: %s, Device: %s", clientIp, RefreshTokenService.getBrowser(deviceInfo), RefreshTokenService.getOs(deviceInfo), RefreshTokenService.getDevice(deviceInfo)),
                user.getEmail()
        );

        LoginResponse.RoleInfo roleInfo = null;
        if (!user.getRoles().isEmpty()) {
            Role primaryRole = user.getRoles().iterator().next();
            roleInfo = LoginResponse.RoleInfo.builder()
                    .id(primaryRole.getId())
                    .name(primaryRole.getName())
                    .build();
        }

        List<LoginResponse.PermissionInfo> permissionInfos = user.getRoles().stream()
                .filter(Role::isActive)
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> LoginResponse.PermissionInfo.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .build())
                .distinct()
                .collect(Collectors.toList());

        LoginResponse.UserLoginInfo userInfo = LoginResponse.UserLoginInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(roleInfo)
                .permissions(permissionInfos)
                .build();

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenEntity.getRefreshToken())
                .tokenType("Bearer")
                .user(userInfo)
                .build();
    }

    @Override
    @Transactional
    public TokenResponse refreshToken(String refreshToken) throws BadRequestException {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BadRequestException("Refresh token is required");
        }

        RefreshToken rt = refreshTokenService.validateAndGetRefreshToken(refreshToken);
        RefreshToken updated = refreshTokenService.verifyAndRefresh(rt);
        User user = updated.getUser();
        String clientIp = updated.getClientIp();
        String deviceInfo = updated.getDeviceInfo();

        log.info("TOKEN REFRESHED | User: {} | Email: {}", user.getUsername(), user.getEmail());

        activityLogService.logActivity(
                ActivityType.TOKEN_REFRESH,
                String.format("Token refreshed. IP: %s, Browser: %s, OS: %s, Device: %s", clientIp, RefreshTokenService.getBrowser(deviceInfo), RefreshTokenService.getOs(deviceInfo), RefreshTokenService.getDevice(deviceInfo)),
                user.getEmail()
        );

        return TokenResponse.builder()
                .accessToken(updated.getAccessToken())
                .refreshToken(updated.getRefreshToken())
                .tokenType("Bearer")
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenService.findByRefreshToken(refreshToken).ifPresent(rt -> {
                User user = rt.getUser();
                String deviceInfo = rt.getDeviceInfo();
                String clientIp = rt.getClientIp();

                refreshTokenService.revokeByRefreshToken(refreshToken);
                log.info("LOGOUT SUCCESS | Refresh token revoked");

                activityLogService.logActivity(
                        ActivityType.LOGOUT,
                        String.format("Logout from device. IP: %s, Browser: %s, OS: %s, Device: %s", clientIp, RefreshTokenService.getBrowser(deviceInfo), RefreshTokenService.getOs(deviceInfo), RefreshTokenService.getDevice(deviceInfo)),
                        user.getEmail()
                );
            });
        }
    }

    @Override
    @Transactional
    public void logoutAll(UUID userId) {
        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourcesNotFoundException("User not found: " + userId));
            
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);

            refreshTokenService.revokeAllByUserId(userId);
            log.info("GLOBAL LOGOUT SUCCESS | All sessions revoked for userId={}", userId);

            activityLogService.logActivity(ActivityType.GLOBAL_LOGOUT,
                    "All sessions terminated for user. Forced logout from all devices.",
                    user.getEmail());
        }
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) throws BadRequestException {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username is already taken: " + request.getUsername());
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email is already registered: " + request.getEmail());
        }

        Set<Role> roles = request.getRoles().stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourcesNotFoundException("Role not found: " + roleName)))
                .collect(Collectors.toSet());

        if (roles.isEmpty()) {
            Role defaultRole = roleRepository.findByName(RoleType.USER.name())
                    .orElseThrow(() -> new ResourcesNotFoundException("Default Role USER not found"));
            roles.add(defaultRole);
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .active(true)
                .locked(false)
                .emailVerified(true) // Set to verified directly to allow immediate login
                .roles(roles)
                .tokenVersion(1L)
                .build();

        User saved = userRepository.save(user);
        log.info("USER REGISTERED SUCCESSFULLY | ID: {} | Email: {}", saved.getId(), saved.getEmail());

        activityLogService.logActivity(
                ActivityType.REGISTER,
                "New user registered",
                saved.getEmail()
        );

        return userMapper.toDto(saved);
    }

    private String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null ? auth.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip.split(",")[0].trim() : "unknown";
    }
}