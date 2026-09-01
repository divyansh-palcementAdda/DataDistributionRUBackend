package com.app.datadistribution.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.useractivity.HeartbeatRequestDTO;
import com.app.datadistribution.dto.useractivity.UserDailyActivityResponseDTO;
import com.app.datadistribution.dto.useractivity.UserInactivityPeriodDTO;
import com.app.datadistribution.dto.useractivity.UserSessionHistoryDTO;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.security.UserDetailsImpl;
import com.app.datadistribution.service.interfaces.IUserActivityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "User Activity Tracking", description = "Endpoints for user heartbeat, login/logout history, and daily CRM activity tracking")
public class UserActivityController {

    private final IUserActivityService userActivityService;

    @PostMapping("/api/user-activity/heartbeat")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send heartbeat to record user activity and maintain active working session")
    public ResponseEntity<ApiResponse<Void>> heartbeat(@RequestBody(required = false) HeartbeatRequestDTO request) throws UnauthorizedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        userActivityService.recordHeartbeat(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Heartbeat recorded successfully", null, HttpStatus.OK.value()));
    }

    @GetMapping("/api/users/{userId}/activity")
    @PreAuthorize("hasAuthority('USER_ACTIVITY_VIEW') or hasAuthority('USER_READ') or hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get daily activity overview, working hours, and CRM metrics for a specific user and date")
    public ResponseEntity<ApiResponse<UserDailyActivityResponseDTO>> getDailyActivity(
            @PathVariable UUID userId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)
            throws UnauthorizedException, AccessDeniedException, BadRequestException, ResourcesNotFoundException {
        UserDailyActivityResponseDTO response = userActivityService.getDailyActivity(userId, date);
        return ResponseEntity.ok(ApiResponse.success("User daily activity retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/api/users/{userId}/activity/sessions")
    @PreAuthorize("hasAuthority('USER_ACTIVITY_DETAILS') or hasAuthority('USER_ACTIVITY_VIEW') or hasAuthority('USER_READ')")
    @Operation(summary = "Get detailed login session history for a specific user and date")
    public ResponseEntity<ApiResponse<List<UserSessionHistoryDTO>>> getSessionHistory(
            @PathVariable UUID userId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)
            throws UnauthorizedException, AccessDeniedException, BadRequestException, ResourcesNotFoundException {
        List<UserSessionHistoryDTO> response = userActivityService.getSessionHistory(userId, date);
        return ResponseEntity.ok(ApiResponse.success("User session history retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/api/users/{userId}/activity/inactivity")
    @PreAuthorize("hasAuthority('USER_ACTIVITY_DETAILS') or hasAuthority('USER_ACTIVITY_VIEW') or hasAuthority('USER_READ')")
    @Operation(summary = "Get detailed inactivity period history for a specific user and date")
    public ResponseEntity<ApiResponse<List<UserInactivityPeriodDTO>>> getInactivityHistory(
            @PathVariable UUID userId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)
            throws UnauthorizedException, AccessDeniedException, BadRequestException, ResourcesNotFoundException {
        List<UserInactivityPeriodDTO> response = userActivityService.getInactivityHistory(userId, date);
        return ResponseEntity.ok(ApiResponse.success("User inactivity history retrieved successfully", response, HttpStatus.OK.value()));
    }
}

