package com.app.datadistribution.notification.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.notification.dto.NotificationCountDTO;
import com.app.datadistribution.notification.dto.NotificationPageResponseDTO;
import com.app.datadistribution.notification.dto.NotificationResponseDTO;
import com.app.datadistribution.notification.service.INotificationRealtimeService;
import com.app.datadistribution.notification.service.INotificationService;
import com.app.datadistribution.security.UserDetailsImpl;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-App Notification and Real-time Stream APIs")
public class NotificationController {

    private final INotificationService notificationService;
    private final INotificationRealtimeService realtimeService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user's paginated notifications")
    public ResponseEntity<ApiResponse<NotificationPageResponseDTO>> getUserNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) throws UnauthorizedException {

        UUID currentUserId = resolveCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));

        NotificationPageResponseDTO response = notificationService.getUserNotifications(currentUserId, unreadOnly, pageable);
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved successfully", response, 200));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get unread and total notification counts for current user")
    public ResponseEntity<ApiResponse<NotificationCountDTO>> getUnreadCount() throws UnauthorizedException {
        UUID currentUserId = resolveCurrentUserId();
        NotificationCountDTO counts = notificationService.getNotificationCounts(currentUserId);
        return ResponseEntity.ok(ApiResponse.success("Notification count retrieved", counts, 200));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark single notification as read")
    public ResponseEntity<ApiResponse<NotificationResponseDTO>> markAsRead(@PathVariable UUID id) throws UnauthorizedException {
        UUID currentUserId = resolveCurrentUserId();
        NotificationResponseDTO response = notificationService.markAsRead(id, currentUserId);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", response, 200));
    }

    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark all notifications as read for current user")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() throws UnauthorizedException {
        UUID currentUserId = resolveCurrentUserId();
        notificationService.markAllAsRead(currentUserId);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null, 200));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete / archive notification for current user")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable UUID id) throws UnauthorizedException {
        UUID currentUserId = resolveCurrentUserId();
        notificationService.deleteNotification(id, currentUserId);
        return ResponseEntity.ok(ApiResponse.success("Notification deleted successfully", null, 200));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Subscribe to authenticated real-time Server-Sent Events (SSE) notification stream")
    public SseEmitter streamNotifications() throws UnauthorizedException {
        UUID currentUserId = resolveCurrentUserId();
        log.info("SSE notification stream connected for user {}", currentUserId);
        return realtimeService.createEmitter(currentUserId);
    }

    private UUID resolveCurrentUserId() throws UnauthorizedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            throw new UnauthorizedException("User is not authenticated");
        }
        return userDetails.getId();
    }
}

