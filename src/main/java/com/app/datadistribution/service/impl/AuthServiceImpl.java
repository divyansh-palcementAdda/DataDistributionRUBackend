package com.app.datadistribution.service.impl;
import java.time.LocalDateTime;
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

import com.app.datadistribution.Model.ActivityType;
import com.app.datadistribution.Model.RefreshToken;
import com.app.datadistribution.Model.Role;
import com.app.datadistribution.Model.User;
import com.app.datadistribution.Model.UserStatus;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.AuthenticationFailedException;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.UserMapper;
import com.app.datadistribution.payload.JwtResponse;
import com.app.datadistribution.payload.LoginRequest;
import com.app.datadistribution.payload.UserDTO;
import com.app.datadistribution.payload.UserRequest;
import com.app.datadistribution.repository.IUserRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.security.JwtProvider;
import com.app.datadistribution.security.UserDetailsImpl;
import com.app.datadistribution.service.interfaces.IActivityLogService;
import com.app.datadistribution.service.interfaces.IAuthService;
import com.app.datadistribution.service.interfaces.IEmailService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final AuthenticationManager authenticationManager;
    private final IUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final UserMapper userMapper;
    private final RefreshTokenService refreshTokenService;
    private final RoleRepository roleRepository;
    private final IActivityLogService activityLogService;
    private final IEmailService emailService;
    private final OtpService otpService;

    @Override
    @Transactional
    public JwtResponse login(@Valid LoginRequest loginRequest, HttpServletRequest httpRequest)
            throws AuthenticationFailedException, AccessDeniedException {
    	String identifier = loginRequest.getEmailOrUsername().trim().toLowerCase();
    	String clientIp = extractClientIp(httpRequest);
        String deviceInfo = httpRequest.getHeader("User-Agent");

        log.info("LOGIN ATTEMPT | Identifier: {} | IP: {}", identifier, clientIp);

        User user = userRepository
        	    .findByEmailIgnoreCaseOrUsernameIgnoreCase(identifier, identifier)
                .orElseThrow(() -> {
                    log.warn("LOGIN FAILED | Invalid credentials for: {}", identifier);
                    activityLogService.logActivity(ActivityType.LOGIN_FAILED,
                            "Failed login attempt for " + identifier, clientIp);
                    return new AuthenticationFailedException("Invalid credentials");
                });

        if (!user.isEmailVerified() || user.getStatus() != UserStatus.ACTIVE) {
            log.warn("LOGIN BLOCKED | Account not active/verified: {} | Status: {}", 
                     user.getEmail(), user.getStatus());
            activityLogService.logActivity(ActivityType.LOGIN_FAILED,
                    "Login blocked - account inactive or unverified", user.getEmail());
            throw new AuthenticationFailedException("Account not active or email not verified");
        }
        Authentication authentication = authenticationManager.authenticate(
        	    new UsernamePasswordAuthenticationToken(user.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        Map<String, Object> claims = Map.of(
            "userId", userDetails.getId(),
            "tokenVersion", userDetails.getTokenVersion()
        );
        String accessToken = jwtProvider.generateAccessToken(userDetails.getUsername(), claims);

        RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(
                user, accessToken, clientIp, deviceInfo);

        log.info("LOGIN SUCCESS | User: {} | ID: {} | IP: {}", 
                 user.getEmail(), user.getUserId(), clientIp);

        // Log successful activity
        activityLogService.logActivity(
                ActivityType.LOGIN,
                "User logged in successfully",
                user.getEmail() + " (" + clientIp + ")"
        );

        return JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenEntity.getRefreshToken())
                .type("Bearer")
                .id(userDetails.getId())
                .username(userDetails.getUsername())
                .email(userDetails.getEmail())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .build();
    }

    @Override
    @Transactional
    public JwtResponse refreshAccessToken(String refreshToken) throws AccessDeniedException, BadRequestException {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BadRequestException("Refresh token is required");
        }

        RefreshToken rt = refreshTokenService.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new AccessDeniedException("Invalid or expired refresh token"));

        RefreshToken updated = refreshTokenService.verifyAndRefresh(rt);
        User user = updated.getUser();

        log.info("TOKEN REFRESHED | User: {} | Email: {} | IP: {}", 
                 user.getUsername(), user.getEmail(), updated.getClientIp());

        activityLogService.logActivity(
                ActivityType.TOKEN_REFRESH,
                "Access token refreshed successfully",
                user.getEmail()
        );

        return JwtResponse.builder()
                .accessToken(updated.getAccessToken())
                .refreshToken(updated.getRefreshToken())
                .type("Bearer")
                .id(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenService.revokeByRefreshToken(refreshToken);
            log.info("LOGOUT SUCCESS | Refresh token revoked");

            // Best effort - get current user if possible
            String currentUser = getCurrentUsername();
            activityLogService.logActivity(ActivityType.LOGOUT, 
                    "User logged out", currentUser != null ? currentUser : "System");
        }
    }

    @Override
    @Transactional
    public void logoutAll(Long userId) {
        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourcesNotFoundException("User not found: " + userId));
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);

            refreshTokenService.revokeAllByUserId(userId);
            log.info("GLOBAL LOGOUT SUCCESS | All sessions revoked for userId={}", userId);

            activityLogService.logActivity(ActivityType.GLOBAL_LOGOUT,
                    "All sessions terminated for user",
                    "Admin/User ID: " + userId);
        }
    }

    @Override
    @Transactional
    public UserDTO register(@Valid UserRequest request)
            throws AccessDeniedException, BadRequestException {

        validateUserRequest(request);

        Optional<User> existingOpt = userRepository.findByEmail(request.getEmail());

        if (existingOpt.isPresent()) {
            User existing = existingOpt.get();
            if (existing.getStatus() == UserStatus.INACTIVE) {
                log.info("REACTIVATING INACTIVE USER | Email: {}", existing.getEmail());

                existing.setStatus(UserStatus.ACTIVE);
                existing.setUpdatedAt(LocalDateTime.now());
                if (StringUtils.hasText(request.getPassword())) {
                    existing.setPassword(passwordEncoder.encode(request.getPassword()));
                }
                Set<Role> roles = resolveRoles(request);
                existing.setRoles(roles);

                User saved = userRepository.save(existing);

                activityLogService.logActivity(ActivityType.USER_REACTIVATED,
                        "Inactive user reactivated during registration",
                        saved.getEmail());

                log.info("USER REACTIVATED SUCCESSFULLY | ID: {}", saved.getUserId());
                return userMapper.toDto(saved);
            } else {
                throw new BadRequestException("Email already registered and active: " + request.getEmail());
            }
        }

        // New user registration
        Set<Role> roles = resolveRoles(request);

        User user = User.builder()
                .username(request.getUsername())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .roles(roles)
                .emailVerified(false)
                .verificationToken(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(user);

        log.info("NEW USER REGISTERED SUCCESSFULLY | ID: {} | Email: {}", 
                 saved.getUserId(), saved.getEmail());

        activityLogService.logActivity(
                ActivityType.REGISTER,
                "New user registered",
                saved.getEmail()
        );

        return userMapper.toDto(saved);
    }

    // ───────────────────────────── Utilities ─────────────────────────────

    private void validateUserRequest(UserRequest req) throws BadRequestException {
        if (req == null) throw new BadRequestException("User registration data is required.");
        if (!StringUtils.hasText(req.getEmail())) throw new BadRequestException("Email is required.");
        if (!StringUtils.hasText(req.getUsername())) throw new BadRequestException("Username is required.");
        if (!StringUtils.hasText(req.getPassword())) throw new BadRequestException("Password is required.");
    }

    private Set<Role> resolveRoles(UserRequest request) throws BadRequestException {
        if (request.getRoles() == null || request.getRoles().isEmpty()) {
            Role defaultRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new ResourcesNotFoundException("Role", "name", "ROLE_USER"));
            return Set.of(defaultRole);
        }

        return request.getRoles().stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourcesNotFoundException("Role", "name", roleName)))
                .collect(Collectors.toSet());
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
        // ... (your existing implementation remains the same)
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip.split(",")[0].trim() : "unknown";
    }
    @Override
    public void sendOtp(String email) throws BadRequestException {
        if (!StringUtils.hasText(email)) {
            throw new BadRequestException("Email is required");
        }
        
        String otp = otpService.generateOtp(email);
        try {
            emailService.sendOtpEmail(email, otp);
            log.info("OTP sent to email: {}", email);
        } catch (Exception e) {
        	log.error("Failed to send OTP email to {}: {}", email, e.getMessage());
        	throw new RuntimeException("Failed to send OTP email");
        }
    }

    @Override
    public void verifyOtp(String email, String otp) throws BadRequestException {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(otp)) {
            throw new BadRequestException("Email and OTP are required");
        }
        
        if (!otpService.verifyOtp(email, otp)) {
            throw new BadRequestException("Invalid or expired OTP");
        }
    }
}