package com.app.datadistribution.notification.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.notification.dto.NotificationPreferenceDTO;
import com.app.datadistribution.notification.service.INotificationPreferenceService;
import com.app.datadistribution.security.UserDetailsImpl;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/notifications/preferences")
@RequiredArgsConstructor
@Tag(name = "Notification Preferences", description = "User In-App and Email Notification Preferences")
public class NotificationPreferenceController {

    private final INotificationPreferenceService preferenceService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get notification preferences for current user")
    public ResponseEntity<ApiResponse<NotificationPreferenceDTO>> getPreferences() throws UnauthorizedException {
        UUID currentUserId = resolveCurrentUserId();
        NotificationPreferenceDTO prefs = preferenceService.getPreferences(currentUserId);
        return ResponseEntity.ok(ApiResponse.success("Notification preferences retrieved", prefs, 200));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update notification preferences for current user")
    public ResponseEntity<ApiResponse<NotificationPreferenceDTO>> updatePreferences(
            @RequestBody NotificationPreferenceDTO dto) throws UnauthorizedException {
        UUID currentUserId = resolveCurrentUserId();
        NotificationPreferenceDTO updated = preferenceService.updatePreferences(currentUserId, dto);
        return ResponseEntity.ok(ApiResponse.success("Notification preferences updated successfully", updated, 200));
    }

    private UUID resolveCurrentUserId() throws UnauthorizedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            throw new UnauthorizedException("User is not authenticated");
        }
        return userDetails.getId();
    }
}

