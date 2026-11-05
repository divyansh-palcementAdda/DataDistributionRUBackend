package com.app.datadistribution.notification.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.notification.dto.AdminNotificationResultDTO;
import com.app.datadistribution.notification.dto.NotificationResponseDTO;
import com.app.datadistribution.notification.dto.SendAdminNotificationRequest;
import com.app.datadistribution.notification.service.INotificationAdminService;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.security.UserDetailsImpl;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/notifications/admin")
@RequiredArgsConstructor
@Tag(name = "Notification Admin", description = "Admin Notification Management and Testing APIs")
public class NotificationAdminController {

    private final INotificationAdminService notificationAdminService;
    private final UserRepository userRepository;

    @PostMapping("/send")
    @PreAuthorize("hasAnyAuthority('SETTINGS_NOTIFICATIONS', 'NOTIFICATION_MANAGE', 'ADMIN', 'ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Send manual notification to user(s), role, or all users")
    public ResponseEntity<ApiResponse<AdminNotificationResultDTO>> sendNotification(
            @Valid @RequestBody SendAdminNotificationRequest request) throws UnauthorizedException {

        User admin = resolveCurrentUser();
        AdminNotificationResultDTO result = notificationAdminService.sendManualNotification(request, admin);
        return ResponseEntity.ok(ApiResponse.success("Notification dispatch completed", result, 200));
    }

    @PostMapping("/test")
    @PreAuthorize("hasAnyAuthority('SETTINGS_NOTIFICATIONS', 'NOTIFICATION_MANAGE', 'ADMIN', 'ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Send test notification to verify end-to-end delivery")
    public ResponseEntity<ApiResponse<NotificationResponseDTO>> sendTestNotification() throws UnauthorizedException {
        User admin = resolveCurrentUser();
        NotificationResponseDTO result = notificationAdminService.sendTestNotification(admin);
        return ResponseEntity.ok(ApiResponse.success("Test notification sent successfully", result, 200));
    }

    private User resolveCurrentUser() throws UnauthorizedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            throw new UnauthorizedException("User is not authenticated");
        }
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new UnauthorizedException("Authenticated admin user not found"));
    }
}

