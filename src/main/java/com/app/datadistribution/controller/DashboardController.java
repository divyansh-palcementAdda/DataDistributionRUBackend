package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.dashboard.*;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.IDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard Management", description = "Centralized endpoints for dynamic, filterable, role-aware CRM dashboard & analytics")
public class DashboardController {

    private final IDashboardService dashboardService;

    @GetMapping("/analytics")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get generic, filterable analytics breakdown by any groupBy dimension")
    public ResponseEntity<ApiResponse<DashboardAnalyticsResponseDTO>> getAnalyticsGet(
            @Valid DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {

        DashboardAnalyticsResponseDTO result = dashboardService.getAnalytics(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("Dashboard analytics retrieved successfully", result, HttpStatus.OK.value()));
    }

    @PostMapping("/analytics")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get generic, filterable analytics breakdown by any groupBy dimension (POST payload)")
    public ResponseEntity<ApiResponse<DashboardAnalyticsResponseDTO>> getAnalyticsPost(
            @Valid @RequestBody DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {

        DashboardAnalyticsResponseDTO result = dashboardService.getAnalytics(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("Dashboard analytics retrieved successfully", result, HttpStatus.OK.value()));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get scope-aware dashboard summary with dynamic sections and metrics")
    public ResponseEntity<ApiResponse<DashboardSummaryDTO>> getDashboardSummary(
            @Valid DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {

        DashboardSummaryDTO summary = dashboardService.getDashboardSummary(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("Dashboard summary retrieved successfully", summary, HttpStatus.OK.value()));
    }

    @GetMapping("/cards")
    @PreAuthorize("hasAuthority('DASHBOARD_CARD_VIEW')")
    @Operation(summary = "Get resolved dashboard cards with role permissions and user preferences applied")
    public ResponseEntity<ApiResponse<List<DashboardCardDTO>>> getResolvedCards() throws UnauthorizedException, BadRequestException {
        List<DashboardCardDTO> cards = dashboardService.getResolvedCards();
        return ResponseEntity.ok(ApiResponse.success("Resolved dashboard cards retrieved successfully", cards, HttpStatus.OK.value()));
    }

    @GetMapping("/lead-status")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get lead status breakdown count for current user scope with dynamic filters")
    public ResponseEntity<ApiResponse<List<GroupCountDTO>>> getLeadStatusBreakdown(
            @Valid DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {

        List<GroupCountDTO> result = dashboardService.getLeadStatusBreakdown(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("Lead status breakdown retrieved successfully", result, HttpStatus.OK.value()));
    }

    @GetMapping("/lead-source")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get lead source breakdown count for current user scope with dynamic filters")
    public ResponseEntity<ApiResponse<List<GroupCountDTO>>> getLeadSourceBreakdown(
            @Valid DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {

        List<GroupCountDTO> result = dashboardService.getLeadSourceBreakdown(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("Lead source breakdown retrieved successfully", result, HttpStatus.OK.value()));
    }

    @GetMapping("/board")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get board breakdown count for current user scope with dynamic filters")
    public ResponseEntity<ApiResponse<List<GroupCountDTO>>> getBoardBreakdown(
            @Valid DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {

        List<GroupCountDTO> result = dashboardService.getBoardBreakdown(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("Board breakdown retrieved successfully", result, HttpStatus.OK.value()));
    }

    @GetMapping("/grade")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get grade breakdown count for current user scope with dynamic filters")
    public ResponseEntity<ApiResponse<List<GroupCountDTO>>> getGradeBreakdown(
            @Valid DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {

        List<GroupCountDTO> result = dashboardService.getGradeBreakdown(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("Grade breakdown retrieved successfully", result, HttpStatus.OK.value()));
    }

    @GetMapping("/course")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get course breakdown count for current user scope with dynamic filters")
    public ResponseEntity<ApiResponse<List<GroupCountDTO>>> getCourseBreakdown(
            @Valid DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {

        List<GroupCountDTO> result = dashboardService.getCourseBreakdown(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("Course breakdown retrieved successfully", result, HttpStatus.OK.value()));
    }

    @GetMapping({"/course-types"})
    @PreAuthorize("hasAuthority('DASHBOARD_COURSE_TYPE_VIEW')")
    @Operation(summary = "Get course type breakdown count for current user scope with dynamic filters")
    public ResponseEntity<ApiResponse<List<GroupCountDTO>>> getCourseTypeBreakdown(
            @Valid DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {

        List<GroupCountDTO> result = dashboardService.getCourseTypeBreakdown(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("Course type breakdown retrieved successfully", result, HttpStatus.OK.value()));
    }

    @GetMapping("/recent-activity")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get recent lead activity audit feed for current user scope")
    public ResponseEntity<ApiResponse<List<Object>>> getRecentActivity() throws UnauthorizedException, BadRequestException {
        List<Object> result = dashboardService.getRecentActivity();
        return ResponseEntity.ok(ApiResponse.success("Recent activity retrieved successfully", result, HttpStatus.OK.value()));
    }

    @GetMapping("/low-data-users")
    @PreAuthorize("hasAuthority('DASHBOARD_LOW_DATA_USERS_VIEW') or hasAuthority('DASHBOARD_LOW_DATA_USERS_READ') or hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get paginated list of low data users for authorized scope")
    public ResponseEntity<ApiResponse<LowDataUserPageResponseDTO>> getLowDataUsers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "remainingDataCount") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws UnauthorizedException, BadRequestException {

        com.app.datadistribution.common.PageRequestDTO pageRequest = com.app.datadistribution.common.PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        LowDataUserPageResponseDTO result = dashboardService.getLowDataUsers(pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Low data users list retrieved successfully", result, HttpStatus.OK.value()));
    }

    @GetMapping("/users-not-logged-in")
    @PreAuthorize("hasAuthority('DASHBOARD_USERS_NOT_LOGGED_IN_VIEW') or hasAuthority('DASHBOARD_USERS_NOT_LOGGED_IN_READ') or hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get paginated list of users who have not logged in today")
    public ResponseEntity<ApiResponse<UserNotLoggedInPageResponseDTO>> getUsersNotLoggedInToday(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "name") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws UnauthorizedException, BadRequestException {

        com.app.datadistribution.common.PageRequestDTO pageRequest = com.app.datadistribution.common.PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        UserNotLoggedInPageResponseDTO result = dashboardService.getUsersNotLoggedInToday(pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Users not logged in today retrieved successfully", result, HttpStatus.OK.value()));
    }

    @GetMapping("/followup-users-not-logged-in-11am")
    @PreAuthorize("hasAuthority('DASHBOARD_FOLLOWUP_USERS_NOT_LOGGED_IN_11AM_VIEW') or hasAuthority('DASHBOARD_FOLLOWUP_USERS_NOT_LOGGED_IN_11AM_READ') or hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get paginated list of users with follow-ups today who did not log in by 11 AM IST")
    public ResponseEntity<ApiResponse<FollowUpUserNotLoggedInPageResponseDTO>> getFollowUpUsersNotLoggedInBy11Am(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "todayFollowUpCount") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws UnauthorizedException, BadRequestException {

        com.app.datadistribution.common.PageRequestDTO pageRequest = com.app.datadistribution.common.PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FollowUpUserNotLoggedInPageResponseDTO result = dashboardService.getFollowUpUsersNotLoggedInBy11Am(pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Follow-up users not logged in by 11 AM IST retrieved successfully", result, HttpStatus.OK.value()));
    }

    @GetMapping("/preferences")
    @PreAuthorize("hasAuthority('DASHBOARD_CARD_VIEW')")
    @Operation(summary = "Get current authenticated user's card preferences")
    public ResponseEntity<ApiResponse<List<UserDashboardPreferenceDTO>>> getUserPreferences() throws UnauthorizedException {
        List<UserDashboardPreferenceDTO> preferences = dashboardService.getUserPreferences();
        return ResponseEntity.ok(ApiResponse.success("User dashboard preferences retrieved successfully", preferences, HttpStatus.OK.value()));
    }

    @PutMapping("/preferences/{cardId}/visibility")
    @PreAuthorize("hasAuthority('DASHBOARD_CARD_PREFERENCE_UPDATE')")
    @Operation(summary = "Update user card visibility preference for authorized card")
    public ResponseEntity<ApiResponse<Void>> updateCardVisibility(
            @PathVariable("cardId") UUID cardId,
            @Valid @RequestBody CardPreferenceUpdateRequest request) throws BadRequestException, UnauthorizedException {

        dashboardService.updateCardVisibility(cardId, Boolean.TRUE.equals(request.getVisible()));
        return ResponseEntity.ok(ApiResponse.success("Card visibility preference updated successfully", null, HttpStatus.OK.value()));
    }

    @PutMapping("/preferences/order")
    @PreAuthorize("hasAuthority('DASHBOARD_CARD_ORDER_UPDATE')")
    @Operation(summary = "Update user card display order preference for authorized cards")
    public ResponseEntity<ApiResponse<Void>> updateCardOrders(
            @Valid @RequestBody CardOrderUpdateRequest request) throws BadRequestException, UnauthorizedException {

        dashboardService.updateCardOrders(request);
        return ResponseEntity.ok(ApiResponse.success("Card display order preference updated successfully", null, HttpStatus.OK.value()));
    }

    @PostMapping("/preferences/reset")
    @PreAuthorize("hasAuthority('DASHBOARD_CARD_PREFERENCE_UPDATE')")
    @Operation(summary = "Reset current user's dashboard preferences back to role defaults")
    public ResponseEntity<ApiResponse<Void>> resetUserPreferences() throws BadRequestException, UnauthorizedException {
        dashboardService.resetUserPreferences();
        return ResponseEntity.ok(ApiResponse.success("Dashboard preferences reset to role defaults successfully", null, HttpStatus.OK.value()));
    }

    // --- Admin Endpoints for Card Role Access & User Preferences ---

    @GetMapping("/admin/cards/{cardId}/roles")
    @PreAuthorize("hasAuthority('ROLE_READ') or hasAuthority('DASHBOARD_VIEW_ALL')")
    @Operation(summary = "Get roles assigned to a dashboard card")
    public ResponseEntity<ApiResponse<List<com.app.datadistribution.dto.user.RoleDTO>>> getCardRoles(@PathVariable("cardId") UUID cardId) throws BadRequestException {
        List<com.app.datadistribution.dto.user.RoleDTO> roles = dashboardService.getCardRoles(cardId);
        return ResponseEntity.ok(ApiResponse.success("Card roles retrieved successfully", roles, HttpStatus.OK.value()));
    }

    @PutMapping("/admin/cards/{cardId}/roles")
    @PreAuthorize("hasAuthority('ROLE_UPDATE') or hasAuthority('DASHBOARD_VIEW_ALL')")
    @Operation(summary = "Assign or revoke roles allowed for a dashboard card")
    public ResponseEntity<ApiResponse<Void>> updateCardRolePermissions(
            @PathVariable("cardId") UUID cardId,
            @Valid @RequestBody CardRoleAssignRequest request) throws BadRequestException {

        dashboardService.updateCardRolePermissions(cardId, request.getRoleIds());
        return ResponseEntity.ok(ApiResponse.success("Card role permissions updated successfully", null, HttpStatus.OK.value()));
    }

    @GetMapping("/admin/users/{userId}/preferences")
    @PreAuthorize("hasAuthority('DASHBOARD_USER_PREFERENCE_MANAGE')")
    @Operation(summary = "Admin: Get dashboard card preferences for a specific user")
    public ResponseEntity<ApiResponse<List<UserDashboardPreferenceDTO>>> getUserPreferencesForUser(@PathVariable("userId") UUID userId) throws BadRequestException, UnauthorizedException {
        List<UserDashboardPreferenceDTO> preferences = dashboardService.getUserPreferencesForUser(userId);
        return ResponseEntity.ok(ApiResponse.success("Target user dashboard preferences retrieved successfully", preferences, HttpStatus.OK.value()));
    }

    @PutMapping("/admin/users/{userId}/preferences/{cardId}/visibility")
    @PreAuthorize("hasAuthority('DASHBOARD_USER_PREFERENCE_MANAGE')")
    @Operation(summary = "Admin: Update card visibility preference for a specific user")
    public ResponseEntity<ApiResponse<Void>> updateCardVisibilityForUser(
            @PathVariable("userId") UUID userId,
            @PathVariable("cardId") UUID cardId,
            @Valid @RequestBody CardPreferenceUpdateRequest request) throws BadRequestException, UnauthorizedException {

        dashboardService.updateCardVisibilityForUser(userId, cardId, Boolean.TRUE.equals(request.getVisible()));
        return ResponseEntity.ok(ApiResponse.success("User card visibility updated successfully", null, HttpStatus.OK.value()));
    }

    @PutMapping("/admin/users/{userId}/preferences/order")
    @PreAuthorize("hasAuthority('DASHBOARD_USER_PREFERENCE_MANAGE')")
    @Operation(summary = "Admin: Update card order preferences for a specific user")
    public ResponseEntity<ApiResponse<Void>> updateCardOrdersForUser(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody CardOrderUpdateRequest request) throws BadRequestException, UnauthorizedException {

        dashboardService.updateCardOrdersForUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success("User card order preferences updated successfully", null, HttpStatus.OK.value()));
    }

    @PostMapping("/admin/users/{userId}/preferences/reset")
    @PreAuthorize("hasAuthority('DASHBOARD_USER_PREFERENCE_MANAGE')")
    @Operation(summary = "Admin: Reset dashboard card preferences for a specific user")
    public ResponseEntity<ApiResponse<Void>> resetPreferencesForUser(@PathVariable("userId") UUID userId) throws BadRequestException, UnauthorizedException {
        dashboardService.resetPreferencesForUser(userId);
        return ResponseEntity.ok(ApiResponse.success("User dashboard preferences reset successfully", null, HttpStatus.OK.value()));
    }
}
