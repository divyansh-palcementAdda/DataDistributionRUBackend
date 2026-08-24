package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.dashboard.*;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IDashboardService {
    DashboardAnalyticsResponseDTO getAnalytics(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException;

    DashboardSummaryDTO getDashboardSummary(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException;
    DashboardSummaryDTO getDashboardSummary(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException;

    List<DashboardCardDTO> getResolvedCards() throws UnauthorizedException, BadRequestException;

    List<GroupCountDTO> getLeadStatusBreakdown(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException;
    List<GroupCountDTO> getLeadStatusBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException;

    List<GroupCountDTO> getLeadSourceBreakdown(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException;
    List<GroupCountDTO> getLeadSourceBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException;

    List<GroupCountDTO> getBoardBreakdown(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException;
    List<GroupCountDTO> getBoardBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException;

    List<GroupCountDTO> getGradeBreakdown(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException;
    List<GroupCountDTO> getGradeBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException;

    List<GroupCountDTO> getCourseBreakdown(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException;
    List<GroupCountDTO> getCourseBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException;

    List<GroupCountDTO> getCourseTypeBreakdown(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException;
    List<GroupCountDTO> getCourseTypeBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException;

    List<Object> getRecentActivity() throws UnauthorizedException, BadRequestException;

    List<UserDashboardPreferenceDTO> getUserPreferences() throws UnauthorizedException;
    void updateCardVisibility(UUID cardId, boolean visible) throws BadRequestException, UnauthorizedException;
    void updateCardOrders(CardOrderUpdateRequest request) throws BadRequestException, UnauthorizedException;
    void resetUserPreferences() throws BadRequestException, UnauthorizedException;

    // Admin APIs for user preference management and card-role permissions
    List<UserDashboardPreferenceDTO> getUserPreferencesForUser(UUID userId) throws BadRequestException, UnauthorizedException;
    void updateCardVisibilityForUser(UUID userId, UUID cardId, boolean visible) throws BadRequestException, UnauthorizedException;
    void updateCardOrdersForUser(UUID userId, CardOrderUpdateRequest request) throws BadRequestException, UnauthorizedException;
    void resetPreferencesForUser(UUID userId) throws BadRequestException, UnauthorizedException;

    List<com.app.datadistribution.dto.user.RoleDTO> getCardRoles(UUID cardId) throws BadRequestException;
    void updateCardRolePermissions(UUID cardId, List<UUID> roleIds) throws BadRequestException;

    long countLowDataUsers() throws UnauthorizedException, BadRequestException;
    com.app.datadistribution.dto.dashboard.LowDataUserPageResponseDTO getLowDataUsers(com.app.datadistribution.common.PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException;

    long countUsersNotLoggedInToday() throws UnauthorizedException, BadRequestException;
    com.app.datadistribution.dto.dashboard.UserNotLoggedInPageResponseDTO getUsersNotLoggedInToday(com.app.datadistribution.common.PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException;

    long countFollowUpUsersNotLoggedInBy11Am() throws UnauthorizedException, BadRequestException;
    com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInPageResponseDTO getFollowUpUsersNotLoggedInBy11Am(com.app.datadistribution.common.PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException;
}
