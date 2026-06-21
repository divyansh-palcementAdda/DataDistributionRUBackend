package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.auth.LoginRequest;
import com.app.datadistribution.dto.auth.LoginResponse;
import com.app.datadistribution.dto.auth.RegisterRequest;
import com.app.datadistribution.dto.auth.TokenResponse;
import com.app.datadistribution.security.UserDetailsImpl;
import com.app.datadistribution.dto.auth.UserProfileResponse;
import com.app.datadistribution.dto.user.PermissionDTO;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.AuthenticationFailedException;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.IAuthService;
import com.app.datadistribution.service.interfaces.IRoleService;
import com.app.datadistribution.service.interfaces.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication and session management")
public class AuthController {

    private final IAuthService authService;
    private final IUserService userService;
    private final UserRepository userRepository;
    private final IRoleService roleService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) throws BadRequestException, AccessDeniedException {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", response, HttpStatus.CREATED.value()));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user credentials and retrieve access/refresh tokens")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) throws AuthenticationFailedException, AccessDeniedException {
    	LoginResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response, HttpStatus.OK.value()));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Request a new access token using a refresh token")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshAccessToken(@RequestBody Map<String, String> requestBody) throws AccessDeniedException, BadRequestException {
        String refreshToken = requestBody.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Refresh token is required in body", HttpStatus.BAD_REQUEST.value()));
        }
        TokenResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Access token refreshed successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user from current device by revoking refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody Map<String, String> requestBody) throws BadRequestException {
        String refreshToken = requestBody.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Refresh token is required in body", HttpStatus.BAD_REQUEST.value()));
        }
        authService.logout(refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null, HttpStatus.OK.value()));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Logout user from all devices by revoking all refresh tokens")
    public ResponseEntity<ApiResponse<Void>> logoutAll() throws UnauthorizedException, BadRequestException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        authService.logoutAll(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Logged out from all devices successfully", null, HttpStatus.OK.value()));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe() throws UnauthorizedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with username: " + username));

        UserProfileResponse.RoleInfo roleInfo = null;
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            Role primaryRole = user.getRoles().iterator().next();
            roleInfo = UserProfileResponse.RoleInfo.builder()
                    .id(primaryRole.getId())
                    .name(primaryRole.getName())
                    .build();
        }

        UserProfileResponse.UserInfo userInfo = UserProfileResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();

        UserProfileResponse response = UserProfileResponse.builder()
                .user(userInfo)
                .role(roleInfo)
                .build();

        return ResponseEntity.ok(ApiResponse.success("User profile fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/my-permissions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get permissions of currently authenticated user")
    public ResponseEntity<ApiResponse<java.util.Collection<PermissionDTO>>> getMyPermissions() throws UnauthorizedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        String username = authentication.getName();
        
        java.util.Collection<PermissionDTO> permissions = roleService.getCachedPermissionsForUser(username);
        
        return ResponseEntity.ok(ApiResponse.success("User permissions fetched successfully", permissions, HttpStatus.OK.value()));
    }
}
