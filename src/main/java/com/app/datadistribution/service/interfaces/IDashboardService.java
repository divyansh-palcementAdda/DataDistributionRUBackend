package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.dashboard.*;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IDashboardService {
    DashboardSummaryDTO getDashboardSummary(LocalDate startDate, LocalDate endDate) throws UnauthorizedException;
    List<DashboardCardDTO> getResolvedCards() throws UnauthorizedException;
    List<GroupCountDTO> getLeadStatusBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException;
    List<GroupCountDTO> getLeadSourceBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException;
    List<GroupCountDTO> getBoardBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException;
    List<GroupCountDTO> getGradeBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException;
    List<GroupCountDTO> getCourseBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException;
    List<Object> getRecentActivity() throws UnauthorizedException;

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
}
