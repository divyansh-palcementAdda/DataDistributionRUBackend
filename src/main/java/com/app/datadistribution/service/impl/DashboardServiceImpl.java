package com.app.datadistribution.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.dashboard.CardOrderUpdateRequest;
import com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest;
import com.app.datadistribution.dto.dashboard.DashboardAnalyticsResponseDTO;
import com.app.datadistribution.dto.dashboard.DashboardCardDTO;
import com.app.datadistribution.dto.dashboard.DashboardSectionDTO;
import com.app.datadistribution.dto.dashboard.DashboardSummaryDTO;
import com.app.datadistribution.dto.dashboard.GroupCountDTO;
import com.app.datadistribution.dto.dashboard.UserDashboardPreferenceDTO;
import com.app.datadistribution.entity.DashboardCard;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFeedback;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.entity.UserDashboardCardPreference;
import com.app.datadistribution.enums.DashboardGroupBy;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.DashboardAnalyticsRepository;
import com.app.datadistribution.repository.DashboardCardRepository;
import com.app.datadistribution.repository.UserDashboardCardPreferenceRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.IDashboardService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements IDashboardService {

    private final DashboardCardRepository dashboardCardRepository;
    private final UserDashboardCardPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;
    private final DashboardAnalyticsRepository dashboardAnalyticsRepository;
    private final com.app.datadistribution.mapper.RoleMapper roleMapper;
    private final com.app.datadistribution.service.interfaces.IDashboardCardPermissionService dashboardCardPermissionService;
    private final com.app.datadistribution.repository.LeadRepository leadRepository;
    private final com.app.datadistribution.repository.ActivityLogRepository activityLogRepository;
    private final com.app.datadistribution.repository.LeadFollowUpRepository leadFollowUpRepository;
    private final EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Value("${app.dashboard.low-data-user-threshold:10}")
    private int lowDataUserThreshold;

    @org.springframework.beans.factory.annotation.Value("${app.dashboard.followup-login-cutoff-time:11:00}")
    private String cutoffTimeStr;

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    public enum DataScope {
        SYSTEM,
        DEPARTMENT,
        SELF
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardAnalyticsResponseDTO getAnalytics(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {
        User currentUser = getCurrentUserEntity();
        DataScope scope = resolveDataScope(currentUser);
        if (filterRequest == null) {
            filterRequest = new DashboardAnalyticsFilterRequest();
        }
        return dashboardAnalyticsRepository.fetchAnalytics(currentUser, scope, filterRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryDTO getDashboardSummary(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {
        User currentUser = getCurrentUserEntity();
        DataScope scope = resolveDataScope(currentUser);

        if (filterRequest == null) {
            filterRequest = new DashboardAnalyticsFilterRequest();
        }

        LocalDate startDate = filterRequest.getEffectiveStartDate();
        LocalDate endDate = filterRequest.getEffectiveEndDate();
        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        long totalLeads = countLeadsInScope(currentUser, scope, startDateTime, endDateTime);
        long totalFollowUpsToday = countFollowUpsTodayInScope(currentUser, scope);
        long loggedToday = userRepository.count((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("lastLogin"), LocalDate.now().atStartOfDay()));
        long currentlyWorking = userRepository.count((root, query, cb) -> cb.and(
                cb.equal(root.get("active"), true),
                cb.greaterThanOrEqualTo(root.get("lastLogin"), LocalDateTime.now().minusHours(8))
        ));

        long feedbackCount = countFeedbacksInScope(currentUser, scope, startDateTime, endDateTime);
        double conversationRatio = totalLeads > 0 ? (double) feedbackCount / totalLeads : 0.0;

        List<DashboardCardDTO> resolvedCards = getResolvedCardsForUser(currentUser);
        populateCardValues(resolvedCards, currentUser, scope, filterRequest);

        Map<String, List<DashboardCardDTO>> groupedBySection = resolvedCards.stream()
                .filter(DashboardCardDTO::isVisible)
                .collect(Collectors.groupingBy(
                        card -> card.getSection() != null ? card.getSection() : "SUMMARY",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<DashboardSectionDTO> sections = new ArrayList<>();
        for (Map.Entry<String, List<DashboardCardDTO>> entry : groupedBySection.entrySet()) {
            sections.add(DashboardSectionDTO.builder()
                    .code(entry.getKey())
                    .name(formatSectionName(entry.getKey()))
                    .cards(entry.getValue())
                    .build());
        }

        return DashboardSummaryDTO.builder()
                .scope(scope.name())
                .totalLeads(totalLeads)
                .totalFollowUpsToday(totalFollowUpsToday)
                .counsellorsLoggedToday(loggedToday)
                .counsellorsCurrentlyWorking(currentlyWorking)
                .conversationRatio(Math.round(conversationRatio * 100.0) / 100.0)
                .sections(sections)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryDTO getDashboardSummary(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException {
        DashboardAnalyticsFilterRequest filterRequest = DashboardAnalyticsFilterRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .build();
        return getDashboardSummary(filterRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DashboardCardDTO> getResolvedCards() throws UnauthorizedException, BadRequestException {
        User currentUser = getCurrentUserEntity();
        DataScope scope = resolveDataScope(currentUser);
        List<DashboardCardDTO> cards = getResolvedCardsForUser(currentUser);
        populateCardValues(cards, currentUser, scope, new DashboardAnalyticsFilterRequest());
        return cards;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCountDTO> getLeadStatusBreakdown(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {
        if (filterRequest == null) filterRequest = new DashboardAnalyticsFilterRequest();
        filterRequest.setGroupBy(DashboardGroupBy.LEAD_STATUS);
        return getAnalytics(filterRequest).getData();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCountDTO> getLeadStatusBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException {
        DashboardAnalyticsFilterRequest req = DashboardAnalyticsFilterRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .groupBy(DashboardGroupBy.LEAD_STATUS)
                .build();
        return getLeadStatusBreakdown(req);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCountDTO> getLeadSourceBreakdown(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {
        if (filterRequest == null) filterRequest = new DashboardAnalyticsFilterRequest();
        filterRequest.setGroupBy(DashboardGroupBy.LEAD_SOURCE);
        return getAnalytics(filterRequest).getData();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCountDTO> getLeadSourceBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException {
        DashboardAnalyticsFilterRequest req = DashboardAnalyticsFilterRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .groupBy(DashboardGroupBy.LEAD_SOURCE)
                .build();
        return getLeadSourceBreakdown(req);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCountDTO> getBoardBreakdown(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {
        if (filterRequest == null) filterRequest = new DashboardAnalyticsFilterRequest();
        filterRequest.setGroupBy(DashboardGroupBy.BOARD);
        return getAnalytics(filterRequest).getData();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCountDTO> getBoardBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException {
        DashboardAnalyticsFilterRequest req = DashboardAnalyticsFilterRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .groupBy(DashboardGroupBy.BOARD)
                .build();
        return getBoardBreakdown(req);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCountDTO> getGradeBreakdown(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {
        if (filterRequest == null) filterRequest = new DashboardAnalyticsFilterRequest();
        filterRequest.setGroupBy(DashboardGroupBy.GRADE);
        return getAnalytics(filterRequest).getData();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCountDTO> getGradeBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException {
        DashboardAnalyticsFilterRequest req = DashboardAnalyticsFilterRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .groupBy(DashboardGroupBy.GRADE)
                .build();
        return getGradeBreakdown(req);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCountDTO> getCourseBreakdown(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {
        if (filterRequest == null) filterRequest = new DashboardAnalyticsFilterRequest();
        filterRequest.setGroupBy(DashboardGroupBy.COURSE);
        return getAnalytics(filterRequest).getData();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCountDTO> getCourseBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException {
        DashboardAnalyticsFilterRequest req = DashboardAnalyticsFilterRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .groupBy(DashboardGroupBy.COURSE)
                .build();
        return getCourseBreakdown(req);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCountDTO> getCourseTypeBreakdown(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {
        if (filterRequest == null) filterRequest = new DashboardAnalyticsFilterRequest();
        filterRequest.setGroupBy(DashboardGroupBy.COURSE_TYPE);
        return getAnalytics(filterRequest).getData();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupCountDTO> getCourseTypeBreakdown(LocalDate startDate, LocalDate endDate) throws UnauthorizedException, BadRequestException {
        DashboardAnalyticsFilterRequest req = DashboardAnalyticsFilterRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .groupBy(DashboardGroupBy.COURSE_TYPE)
                .build();
        return getCourseTypeBreakdown(req);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Object> getRecentActivity() throws UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        DataScope scope = resolveDataScope(currentUser);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<LeadStatusHistory> query = cb.createQuery(LeadStatusHistory.class);
        Root<LeadStatusHistory> root = query.from(LeadStatusHistory.class);

        Predicate spec = cb.conjunction();
        if (scope == DataScope.SELF) {
            spec = cb.and(spec, cb.equal(root.get("changedByUser").get("id"), currentUser.getId()));
        }

        query.where(spec);
        query.orderBy(cb.desc(root.get("createdAt")));

        List<LeadStatusHistory> histories = entityManager.createQuery(query)
                .setMaxResults(15)
                .getResultList();

        List<Object> activities = new ArrayList<>();
        for (LeadStatusHistory h : histories) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", h.getId());
            item.put("leadCode", h.getLead() != null ? h.getLead().getLeadCode() : null);
            item.put("previousStatus", h.getPreviousStatus() != null ? h.getPreviousStatus().getName() : null);
            item.put("newStatus", h.getNewStatus() != null ? h.getNewStatus().getName() : null);
            item.put("changedBy", h.getChangedByUser() != null ? h.getChangedByUser().getFirstName() + " " + h.getChangedByUser().getLastName() : null);
            item.put("feedback", h.getFeedback());
            item.put("timestamp", h.getCreatedAt());
            activities.add(item);
        }
        return activities;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDashboardPreferenceDTO> getUserPreferences() throws UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        List<UserDashboardCardPreference> prefs = userPreferenceRepository.findByUserIdOrderByDisplayOrderAsc(currentUser.getId());
        return prefs.stream().map(p -> UserDashboardPreferenceDTO.builder()
                .cardId(p.getDashboardCard().getId())
                .cardCode(p.getDashboardCard().getCode())
                .cardName(p.getDashboardCard().getName())
                .section(p.getDashboardCard().getSection())
                .visible(p.isVisible())
                .displayOrder(p.getDisplayOrder())
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateCardVisibility(UUID cardId, boolean visible) throws BadRequestException, UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        if (!hasPermission(currentUser, PermissionType.DASHBOARD_CARD_PREFERENCE_UPDATE.name())) {
            throw new BadRequestException("You do not have permission to update dashboard card visibility preferences");
        }

        DashboardCard card = dashboardCardRepository.findById(cardId)
                .filter(DashboardCard::isActive)
                .orElseThrow(() -> new ResourcesNotFoundException("Dashboard card not found: " + cardId));

        if (!isCardAllowedForUserRoles(card, currentUser)) {
            throw new BadRequestException("You are not authorized to access card: " + card.getName());
        }

        UserDashboardCardPreference preference = userPreferenceRepository.findByUserIdAndDashboardCardId(currentUser.getId(), cardId)
                .orElseGet(() -> UserDashboardCardPreference.builder()
                        .user(currentUser)
                        .dashboardCard(card)
                        .displayOrder(card.getDisplayOrder())
                        .build());

        preference.setVisible(visible);
        userPreferenceRepository.save(preference);
        log.info("Updated card visibility for user {} card {} to {}", currentUser.getUsername(), card.getCode(), visible);
    }

    @Override
    @Transactional
    public void updateCardOrders(CardOrderUpdateRequest request) throws BadRequestException, UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        if (!hasPermission(currentUser, PermissionType.DASHBOARD_CARD_ORDER_UPDATE.name())) {
            throw new BadRequestException("You do not have permission to update dashboard card display order");
        }

        if (request.getCardOrders() == null) return;

        for (CardOrderUpdateRequest.CardOrderItem item : request.getCardOrders()) {
            DashboardCard card = dashboardCardRepository.findById(item.getCardId())
                    .filter(DashboardCard::isActive)
                    .orElseThrow(() -> new ResourcesNotFoundException("Dashboard card not found: " + item.getCardId()));

            if (!isCardAllowedForUserRoles(card, currentUser)) {
                throw new BadRequestException("You are not authorized to access card: " + card.getName());
            }

            UserDashboardCardPreference preference = userPreferenceRepository.findByUserIdAndDashboardCardId(currentUser.getId(), card.getId())
                    .orElseGet(() -> UserDashboardCardPreference.builder()
                            .user(currentUser)
                            .dashboardCard(card)
                            .visible(true)
                            .build());

            preference.setDisplayOrder(item.getDisplayOrder());
            userPreferenceRepository.save(preference);
        }
        log.info("Updated card display order for user {}", currentUser.getUsername());
    }

    @Override
    @Transactional
    public void resetUserPreferences() throws BadRequestException, UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        if (!hasPermission(currentUser, PermissionType.DASHBOARD_CARD_PREFERENCE_UPDATE.name())) {
            throw new BadRequestException("You do not have permission to reset dashboard preferences");
        }
        userPreferenceRepository.deleteByUserId(currentUser.getId());
        log.info("Reset dashboard preferences for user {}", currentUser.getUsername());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDashboardPreferenceDTO> getUserPreferencesForUser(UUID userId) throws BadRequestException, UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        if (!hasPermission(currentUser, PermissionType.DASHBOARD_USER_PREFERENCE_MANAGE.name()) && !isAdmin(currentUser)) {
            throw new BadRequestException("You do not have permission to manage user dashboard preferences");
        }
        User targetUser = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found: " + userId));

        List<UserDashboardCardPreference> prefs = userPreferenceRepository.findByUserIdOrderByDisplayOrderAsc(targetUser.getId());
        return prefs.stream().map(p -> UserDashboardPreferenceDTO.builder()
                .cardId(p.getDashboardCard().getId())
                .cardCode(p.getDashboardCard().getCode())
                .cardName(p.getDashboardCard().getName())
                .section(p.getDashboardCard().getSection())
                .visible(p.isVisible())
                .displayOrder(p.getDisplayOrder())
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateCardVisibilityForUser(UUID userId, UUID cardId, boolean visible) throws BadRequestException, UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        if (!hasPermission(currentUser, PermissionType.DASHBOARD_USER_PREFERENCE_MANAGE.name()) && !isAdmin(currentUser)) {
            throw new BadRequestException("You do not have permission to manage user dashboard preferences");
        }
        User targetUser = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found: " + userId));

        DashboardCard card = dashboardCardRepository.findById(cardId)
                .filter(DashboardCard::isActive)
                .orElseThrow(() -> new ResourcesNotFoundException("Dashboard card not found: " + cardId));

        UserDashboardCardPreference preference = userPreferenceRepository.findByUserIdAndDashboardCardId(targetUser.getId(), cardId)
                .orElseGet(() -> UserDashboardCardPreference.builder()
                        .user(targetUser)
                        .dashboardCard(card)
                        .displayOrder(card.getDisplayOrder())
                        .build());

        preference.setVisible(visible);
        userPreferenceRepository.save(preference);
        log.info("Admin {} updated card visibility for user {} card {} to {}", currentUser.getUsername(), targetUser.getUsername(), card.getCode(), visible);
    }

    @Override
    @Transactional
    public void updateCardOrdersForUser(UUID userId, CardOrderUpdateRequest request) throws BadRequestException, UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        if (!hasPermission(currentUser, PermissionType.DASHBOARD_USER_PREFERENCE_MANAGE.name()) && !isAdmin(currentUser)) {
            throw new BadRequestException("You do not have permission to manage user dashboard preferences");
        }
        User targetUser = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found: " + userId));

        if (request.getCardOrders() == null) return;

        for (CardOrderUpdateRequest.CardOrderItem item : request.getCardOrders()) {
            DashboardCard card = dashboardCardRepository.findById(item.getCardId())
                    .filter(DashboardCard::isActive)
                    .orElseThrow(() -> new ResourcesNotFoundException("Dashboard card not found: " + item.getCardId()));

            UserDashboardCardPreference preference = userPreferenceRepository.findByUserIdAndDashboardCardId(targetUser.getId(), card.getId())
                    .orElseGet(() -> UserDashboardCardPreference.builder()
                            .user(targetUser)
                            .dashboardCard(card)
                            .visible(true)
                            .build());

            preference.setDisplayOrder(item.getDisplayOrder());
            userPreferenceRepository.save(preference);
        }
        log.info("Admin {} updated card orders for user {}", currentUser.getUsername(), targetUser.getUsername());
    }

    @Override
    @Transactional
    public void resetPreferencesForUser(UUID userId) throws BadRequestException, UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        if (!hasPermission(currentUser, PermissionType.DASHBOARD_USER_PREFERENCE_MANAGE.name()) && !isAdmin(currentUser)) {
            throw new BadRequestException("You do not have permission to manage user dashboard preferences");
        }
        User targetUser = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found: " + userId));

        userPreferenceRepository.deleteByUserId(targetUser.getId());
        log.info("Admin {} reset dashboard preferences for user {}", currentUser.getUsername(), targetUser.getUsername());
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.app.datadistribution.dto.user.RoleDTO> getCardRoles(UUID cardId) throws BadRequestException {
        DashboardCard card = dashboardCardRepository.findById(cardId)
                .filter(DashboardCard::isActive)
                .orElseThrow(() -> new ResourcesNotFoundException("Dashboard card not found: " + cardId));

        return card.getAllowedRoles().stream()
                .filter(r -> !r.isDeleted())
                .map(roleMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateCardRolePermissions(UUID cardId, List<UUID> roleIds) throws BadRequestException {
        dashboardCardPermissionService.updateCardRolePermissions(cardId, roleIds);
    }

    // --- Private Helper Methods ---

    private User getCurrentUserEntity() throws UnauthorizedException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with username: " + username));
    }

    private DataScope resolveDataScope(User user) {
        if (hasPermission(user, PermissionType.DASHBOARD_VIEW_ALL.name()) || isAdmin(user)) {
            return DataScope.SYSTEM;
        }
        if (hasPermission(user, PermissionType.DASHBOARD_VIEW_DEPARTMENT.name()) || isHOD(user)) {
            return DataScope.DEPARTMENT;
        }
        return DataScope.SELF;
    }

    private boolean isAdmin(User user) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream()
                .anyMatch(r -> RoleType.SUPER_ADMIN.name().equalsIgnoreCase(r.getName())
                        || RoleType.ADMIN.name().equalsIgnoreCase(r.getName()));
    }

    private boolean isHOD(User user) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream()
                .anyMatch(r -> r.getName().toUpperCase().contains("HOD") || r.getName().toUpperCase().contains("HEAD"));
    }

    private boolean hasPermission(User user, String permName) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .anyMatch(p -> p.getName().equalsIgnoreCase(permName));
    }

    private boolean isCardAllowedForUserRoles(DashboardCard card, User user) {
        if (user.getRoles() != null && user.getRoles().stream().anyMatch(r -> RoleType.SUPER_ADMIN.name().equalsIgnoreCase(r.getName()))) {
            return true;
        }
        if (card.getPermission() != null) {
            return hasPermission(user, card.getPermission().getName());
        }
        if (card.getAllowedRoles() == null || card.getAllowedRoles().isEmpty()) return true;
        return user.getRoles().stream().anyMatch(r -> card.getAllowedRoles().contains(r));
    }

    private List<DashboardCardDTO> getResolvedCardsForUser(User user) {
        List<DashboardCard> allActiveCards = dashboardCardRepository.findAllByActiveTrueOrderByDisplayOrderAsc();
        List<DashboardCard> permittedCards = allActiveCards.stream()
                .filter(card -> isCardAllowedForUserRoles(card, user))
                .collect(Collectors.toList());

        List<UserDashboardCardPreference> userPrefs = userPreferenceRepository.findByUserId(user.getId());
        Map<UUID, UserDashboardCardPreference> prefMap = userPrefs.stream()
                .collect(Collectors.toMap(p -> p.getDashboardCard().getId(), p -> p, (a, b) -> a));

        boolean canUpdateVisibility = hasPermission(user, PermissionType.DASHBOARD_CARD_PREFERENCE_UPDATE.name()) || isAdmin(user);
        boolean canUpdateOrder = hasPermission(user, PermissionType.DASHBOARD_CARD_ORDER_UPDATE.name()) || isAdmin(user);

        List<DashboardCardDTO> dtos = new ArrayList<>();
        for (DashboardCard card : permittedCards) {
            UserDashboardCardPreference pref = prefMap.get(card.getId());

            boolean visible = true;
            if (pref != null && canUpdateVisibility) {
                visible = pref.isVisible();
            }

            int order = card.getDisplayOrder();
            if (pref != null && canUpdateOrder && pref.getDisplayOrder() != null) {
                order = pref.getDisplayOrder();
            }

            dtos.add(DashboardCardDTO.builder()
                    .id(card.getId())
                    .code(card.getCode())
                    .name(card.getName())
                    .description(card.getDescription())
                    .section(card.getSection())
                    .cardType(card.getCardType())
                    .icon(card.getIcon())
                    .displayOrder(order)
                    .visible(visible)
                    .build());
        }

        dtos.sort(Comparator.comparingInt(DashboardCardDTO::getDisplayOrder));
        return dtos;
    }

    private void populateCardValues(List<DashboardCardDTO> cards, User user, DataScope scope, DashboardAnalyticsFilterRequest filter) throws UnauthorizedException, BadRequestException {
        LocalDate startDate = filter != null ? filter.getEffectiveStartDate() : null;
        LocalDate endDate = filter != null ? filter.getEffectiveEndDate() : null;
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        for (DashboardCardDTO card : cards) {
            if (!card.isVisible()) continue;
            switch (card.getCode()) {
                case "TOTAL_LEADS":
                    card.setValue(countLeadsInScope(user, scope, start, end));
                    break;
                case "TOTAL_FOLLOWUPS_TODAY":
                    card.setValue(countFollowUpsTodayInScope(user, scope));
                    break;
                case "TOTAL_COUNSELLORS_LOGGED_TODAY":
                    card.setValue(userRepository.count((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("lastLogin"), LocalDate.now().atStartOfDay())));
                    break;
                case "TOTAL_COUNSELLORS_WORKING":
                    card.setValue(userRepository.count((root, query, cb) -> cb.and(
                            cb.equal(root.get("active"), true),
                            cb.greaterThanOrEqualTo(root.get("lastLogin"), LocalDateTime.now().minusHours(8))
                    )));
                    break;
                case "CONVERSATION_RATIO":
                    long totalLeads = countLeadsInScope(user, scope, start, end);
                    long feedbackCount = countFeedbacksInScope(user, scope, start, end);
                    double ratio = totalLeads > 0 ? (double) feedbackCount / totalLeads : 0.0;
                    card.setValue(Math.round(ratio * 100.0) / 100.0);
                    break;
                case "LEAD_STATUS_GROUP":
                    card.setGroupData(getLeadStatusBreakdown(filter));
                    break;
                case "LEAD_SOURCE_GROUP":
                    card.setGroupData(getLeadSourceBreakdown(filter));
                    break;
                case "BOARD_GROUP":
                    card.setGroupData(getBoardBreakdown(filter));
                    break;
                case "GRADE_GROUP":
                    card.setGroupData(getGradeBreakdown(filter));
                    break;
                case "COURSE_GROUP":
                    card.setGroupData(getCourseBreakdown(filter));
                    break;
                case "COURSE_TYPE_GROUP":
                    card.setGroupData(getCourseTypeBreakdown(filter));
                    break;
                case "RECENT_ACTIVITY":
                    try {
                        card.setValue(getRecentActivity());
                    } catch (Exception e) {
                        log.error("Failed to load recent activity for dashboard card", e);
                    }
                    break;
                case "LOW_DATA_USERS":
                    try {
                        card.setValue(countLowDataUsers());
                    } catch (Exception e) {
                        log.error("Failed to load low data users count for dashboard card", e);
                        card.setValue(0);
                    }
                    break;
                case "USERS_NOT_LOGGED_IN":
                    try {
                        card.setValue(countUsersNotLoggedInToday());
                    } catch (Exception e) {
                        log.error("Failed to load users not logged in count for dashboard card", e);
                        card.setValue(0);
                    }
                    break;
                case "FOLLOWUP_USERS_NOT_LOGGED_IN_11AM":
                    try {
                        card.setValue(countFollowUpUsersNotLoggedInBy11Am());
                    } catch (Exception e) {
                        log.error("Failed to load follow-up users not logged in count for dashboard card", e);
                        card.setValue(0);
                    }
                    break;
                default:
                    card.setValue(0);
                    break;
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countLowDataUsers() throws UnauthorizedException, BadRequestException {
        User currentUser = getCurrentUserEntity();
        DataScope scope = resolveDataScope(currentUser);
        return findLowDataUserSummaries(currentUser, scope).size();
    }

    @Override
    @Transactional(readOnly = true)
    public com.app.datadistribution.dto.dashboard.LowDataUserPageResponseDTO getLowDataUsers(com.app.datadistribution.common.PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException {
        User currentUser = getCurrentUserEntity();
        DataScope scope = resolveDataScope(currentUser);

        List<com.app.datadistribution.dto.dashboard.LowDataUserSummaryDTO> allLowDataUsers = findLowDataUserSummaries(currentUser, scope);

        if (pageRequest == null) {
            pageRequest = new com.app.datadistribution.common.PageRequestDTO();
        }

        String search = pageRequest.getSearch();
        if (search != null && !search.isBlank()) {
            String searchPattern = search.toLowerCase();
            allLowDataUsers = allLowDataUsers.stream()
                    .filter(u -> (u.getName() != null && u.getName().toLowerCase().contains(searchPattern))
                            || (u.getUsername() != null && u.getUsername().toLowerCase().contains(searchPattern))
                            || (u.getEmail() != null && u.getEmail().toLowerCase().contains(searchPattern)))
                    .collect(Collectors.toList());
        }

        String sortBy = pageRequest.getSortBy();
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "remainingDataCount";
        }
        boolean isAsc = pageRequest.getSortDirection() == null || !pageRequest.getSortDirection().equalsIgnoreCase("DESC");

        Comparator<com.app.datadistribution.dto.dashboard.LowDataUserSummaryDTO> comparator;
        switch (sortBy) {
            case "allottedDataCount":
                comparator = Comparator.comparingLong(com.app.datadistribution.dto.dashboard.LowDataUserSummaryDTO::getAllottedDataCount);
                break;
            case "availedDataCount":
                comparator = Comparator.comparingLong(com.app.datadistribution.dto.dashboard.LowDataUserSummaryDTO::getAvailedDataCount);
                break;
            case "name":
                comparator = Comparator.comparing(com.app.datadistribution.dto.dashboard.LowDataUserSummaryDTO::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "remainingDataCount":
            default:
                comparator = Comparator.comparingLong(com.app.datadistribution.dto.dashboard.LowDataUserSummaryDTO::getRemainingDataCount);
                break;
        }

        if (!isAsc) {
            comparator = comparator.reversed();
        }
        allLowDataUsers.sort(comparator);

        int page = Math.max(0, pageRequest.getPage());
        int size = pageRequest.getSize() > 0 ? pageRequest.getSize() : 20;
        int totalElements = allLowDataUsers.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        int start = Math.min(page * size, totalElements);
        int end = Math.min(start + size, totalElements);
        List<com.app.datadistribution.dto.dashboard.LowDataUserSummaryDTO> pagedContent = allLowDataUsers.subList(start, end);

        return com.app.datadistribution.dto.dashboard.LowDataUserPageResponseDTO.builder()
                .content(pagedContent)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(page >= totalPages - 1)
                .build();
    }

    private List<com.app.datadistribution.dto.dashboard.LowDataUserSummaryDTO> findLowDataUserSummaries(User currentUser, DataScope scope) {
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(u -> u.isActive() && !u.isDeleted())
                .collect(Collectors.toList());

        if (scope == DataScope.SELF) {
            activeUsers = activeUsers.stream()
                    .filter(u -> u.getId().equals(currentUser.getId()))
                    .collect(Collectors.toList());
        } else if (scope == DataScope.DEPARTMENT) {
            Set<UUID> allowedDeptIds = currentUser.getDepartments() != null
                    ? currentUser.getDepartments().stream().map(com.app.datadistribution.entity.Department::getId).collect(Collectors.toSet())
                    : Collections.emptySet();
            activeUsers = activeUsers.stream()
                    .filter(u -> u.getDepartments() != null && u.getDepartments().stream().anyMatch(d -> allowedDeptIds.contains(d.getId())))
                    .collect(Collectors.toList());
        }

        List<Object[]> allottedResults = leadRepository.findAllottedLeadCountsGroupedByUser();
        Map<UUID, Long> allottedMap = new HashMap<>();
        for (Object[] row : allottedResults) {
            UUID uId = (UUID) row[0];
            Long count = (Long) row[1];
            if (uId != null) allottedMap.put(uId, count);
        }

        List<Object[]> unavailedResults = leadRepository.findUnavailedLeadCountsGroupedByUser();
        Map<UUID, Long> unavailedMap = new HashMap<>();
        for (Object[] row : unavailedResults) {
            UUID uId = (UUID) row[0];
            Long count = (Long) row[1];
            if (uId != null) unavailedMap.put(uId, count);
        }

        List<com.app.datadistribution.dto.dashboard.LowDataUserSummaryDTO> lowDataUsers = new ArrayList<>();
        for (User u : activeUsers) {
            long allotted = allottedMap.getOrDefault(u.getId(), 0L);
            long unavailed = unavailedMap.getOrDefault(u.getId(), 0L);
            long availed = Math.max(0, allotted - unavailed);

            if (unavailed < lowDataUserThreshold) {
                List<String> roles = u.getRoles() != null
                        ? u.getRoles().stream().map(com.app.datadistribution.entity.Role::getName).collect(Collectors.toList())
                        : Collections.emptyList();
                List<String> depts = u.getDepartments() != null
                        ? u.getDepartments().stream().map(com.app.datadistribution.entity.Department::getName).collect(Collectors.toList())
                        : Collections.emptyList();

                lowDataUsers.add(com.app.datadistribution.dto.dashboard.LowDataUserSummaryDTO.builder()
                        .userId(u.getId())
                        .name(u.getFirstName() + " " + u.getLastName())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .roleNames(roles)
                        .departmentNames(depts)
                        .allottedDataCount(allotted)
                        .availedDataCount(availed)
                        .remainingDataCount(unavailed)
                        .isLowDataUser(true)
                        .build());
            }
        }
        return lowDataUsers;
    }

    private long countLeadsInScope(User user, DataScope scope, LocalDateTime start, LocalDateTime end) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Lead> root = query.from(Lead.class);

        Predicate spec = cb.equal(root.get("isDeleted"), false);
        spec = applyScopePredicate(cb, root, spec, user, scope);

        if (start != null) {
            spec = cb.and(spec, cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (end != null) {
            spec = cb.and(spec, cb.lessThanOrEqualTo(root.get("createdAt"), end));
        }

        query.select(cb.count(root)).where(spec);
        return entityManager.createQuery(query).getSingleResult();
    }

    private long countFollowUpsTodayInScope(User user, DataScope scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<LeadFollowUp> root = query.from(LeadFollowUp.class);

        Predicate spec = cb.equal(root.get("isDeleted"), false);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        spec = cb.and(spec, cb.between(root.get("followUpDate"), todayStart, todayEnd));

        if (scope == DataScope.SELF) {
            spec = cb.and(spec, cb.equal(root.get("createdByUser").get("id"), user.getId()));
        }

        query.select(cb.count(root)).where(spec);
        return entityManager.createQuery(query).getSingleResult();
    }

    private long countFeedbacksInScope(User user, DataScope scope, LocalDateTime start, LocalDateTime end) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<LeadFeedback> root = query.from(LeadFeedback.class);

        Predicate spec = cb.equal(root.get("isDeleted"), false);
        if (scope == DataScope.SELF) {
            spec = cb.and(spec, cb.equal(root.get("createdByUser").get("id"), user.getId()));
        }

        if (start != null) {
            spec = cb.and(spec, cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (end != null) {
            spec = cb.and(spec, cb.lessThanOrEqualTo(root.get("createdAt"), end));
        }

        query.select(cb.count(root)).where(spec);
        return entityManager.createQuery(query).getSingleResult();
    }

    private Predicate applyScopePredicate(CriteriaBuilder cb, Root<Lead> root, Predicate spec, User user, DataScope scope) {
        if (scope == DataScope.SELF) {
            return cb.and(spec, cb.or(
                    cb.equal(root.get("assignedTo").get("id"), user.getId()),
                    cb.equal(root.get("createdByUser").get("id"), user.getId())
            ));
        }
        return spec;
    }

    private String formatSectionName(String sectionCode) {
        if (sectionCode == null) return "Overview";
        switch (sectionCode.toUpperCase()) {
            case "SUMMARY": return "Summary";
            case "LEAD_STATUS": return "Lead Status Overview";
            case "LEAD_SOURCE": return "Lead Source Breakdown";
            case "BOARD": return "Board Wise Overview";
            case "GRADE": return "Grade Wise Overview";
            case "COURSE": return "Course Wise Overview";
            case "COURSE_TYPE": return "Course Type Wise Overview";
            case "ACTIVITY": return "Recent System Activity";
            default: return sectionCode;
        }
    }

    private LocalTime parseCutoffTime() {
        try {
            return LocalTime.parse(cutoffTimeStr.trim());
        } catch (Exception e) {
            return LocalTime.of(11, 0);
        }
    }

    private List<User> getActiveMonitoredUsersInScope(User currentUser, DataScope scope) {
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(u -> u.isActive() && !u.isDeleted())
                .collect(Collectors.toList());

        if (scope == DataScope.SELF) {
            activeUsers = activeUsers.stream()
                    .filter(u -> u.getId().equals(currentUser.getId()))
                    .collect(Collectors.toList());
        } else if (scope == DataScope.DEPARTMENT) {
            Set<UUID> allowedDeptIds = currentUser.getDepartments() != null
                    ? currentUser.getDepartments().stream().map(com.app.datadistribution.entity.Department::getId).collect(Collectors.toSet())
                    : Collections.emptySet();
            activeUsers = activeUsers.stream()
                    .filter(u -> u.getDepartments() != null && u.getDepartments().stream().anyMatch(d -> allowedDeptIds.contains(d.getId())))
                    .collect(Collectors.toList());
        }
        return activeUsers;
    }

    @Override
    @Transactional(readOnly = true)
    public long countUsersNotLoggedInToday() throws UnauthorizedException, BadRequestException {
        User currentUser = getCurrentUserEntity();
        DataScope scope = resolveDataScope(currentUser);
        return findUsersNotLoggedInToday(currentUser, scope).size();
    }

    @Override
    @Transactional(readOnly = true)
    public com.app.datadistribution.dto.dashboard.UserNotLoggedInPageResponseDTO getUsersNotLoggedInToday(com.app.datadistribution.common.PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException {
        User currentUser = getCurrentUserEntity();
        DataScope scope = resolveDataScope(currentUser);

        List<com.app.datadistribution.dto.dashboard.UserNotLoggedInSummaryDTO> allItems = findUsersNotLoggedInToday(currentUser, scope);

        if (pageRequest == null) {
            pageRequest = new com.app.datadistribution.common.PageRequestDTO();
        }

        String search = pageRequest.getSearch();
        if (search != null && !search.isBlank()) {
            String searchPattern = search.toLowerCase();
            allItems = allItems.stream()
                    .filter(u -> (u.getName() != null && u.getName().toLowerCase().contains(searchPattern))
                            || (u.getUsername() != null && u.getUsername().toLowerCase().contains(searchPattern))
                            || (u.getEmail() != null && u.getEmail().toLowerCase().contains(searchPattern)))
                    .collect(Collectors.toList());
        }

        String sortBy = pageRequest.getSortBy();
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "name";
        }
        boolean isAsc = pageRequest.getSortDirection() == null || !pageRequest.getSortDirection().equalsIgnoreCase("DESC");

        Comparator<com.app.datadistribution.dto.dashboard.UserNotLoggedInSummaryDTO> comparator;
        switch (sortBy) {
            case "lastSuccessfulLoginAt":
                comparator = Comparator.comparing(com.app.datadistribution.dto.dashboard.UserNotLoggedInSummaryDTO::getLastSuccessfulLoginAt, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "username":
                comparator = Comparator.comparing(com.app.datadistribution.dto.dashboard.UserNotLoggedInSummaryDTO::getUsername, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "name":
            default:
                comparator = Comparator.comparing(com.app.datadistribution.dto.dashboard.UserNotLoggedInSummaryDTO::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
        }

        if (!isAsc) {
            comparator = comparator.reversed();
        }
        allItems.sort(comparator);

        int page = Math.max(0, pageRequest.getPage());
        int size = pageRequest.getSize() > 0 ? pageRequest.getSize() : 20;
        int totalElements = allItems.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        int start = Math.min(page * size, totalElements);
        int end = Math.min(start + size, totalElements);
        List<com.app.datadistribution.dto.dashboard.UserNotLoggedInSummaryDTO> pagedContent = allItems.subList(start, end);

        return com.app.datadistribution.dto.dashboard.UserNotLoggedInPageResponseDTO.builder()
                .content(pagedContent)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(page >= totalPages - 1)
                .build();
    }

    private List<com.app.datadistribution.dto.dashboard.UserNotLoggedInSummaryDTO> findUsersNotLoggedInToday(User currentUser, DataScope scope) {
        ZonedDateTime nowIST = ZonedDateTime.now(IST_ZONE);
        LocalDateTime startOfDayIST = nowIST.toLocalDate().atStartOfDay();
        LocalDateTime endOfDayIST = nowIST.toLocalDate().atTime(LocalTime.MAX);

        List<User> activeUsers = getActiveMonitoredUsersInScope(currentUser, scope);

        List<Object[]> loginStats = activityLogRepository.findDailyLoginStatsGroupedByPerformedBy(startOfDayIST, endOfDayIST);
        Set<String> loggedInPerformersToday = new HashSet<>();
        for (Object[] row : loginStats) {
            String perf = (String) row[0];
            if (perf != null) loggedInPerformersToday.add(perf.toLowerCase());
        }

        List<com.app.datadistribution.dto.dashboard.UserNotLoggedInSummaryDTO> result = new ArrayList<>();
        for (User u : activeUsers) {
            boolean hasLoggedIn = (u.getEmail() != null && loggedInPerformersToday.contains(u.getEmail().toLowerCase()))
                    || (u.getUsername() != null && loggedInPerformersToday.contains(u.getUsername().toLowerCase()))
                    || (u.getLastLogin() != null && !u.getLastLogin().isBefore(startOfDayIST));

            if (!hasLoggedIn) {
                List<String> roles = u.getRoles() != null
                        ? u.getRoles().stream().map(com.app.datadistribution.entity.Role::getName).collect(Collectors.toList())
                        : Collections.emptyList();
                List<String> depts = u.getDepartments() != null
                        ? u.getDepartments().stream().map(com.app.datadistribution.entity.Department::getName).collect(Collectors.toList())
                        : Collections.emptyList();

                result.add(com.app.datadistribution.dto.dashboard.UserNotLoggedInSummaryDTO.builder()
                        .userId(u.getId())
                        .name(u.getFirstName() + " " + u.getLastName())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .roleNames(roles)
                        .departmentNames(depts)
                        .lastSuccessfulLoginAt(u.getLastLogin())
                        .hasLoggedInToday(false)
                        .build());
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public long countFollowUpUsersNotLoggedInBy11Am() throws UnauthorizedException, BadRequestException {
        User currentUser = getCurrentUserEntity();
        DataScope scope = resolveDataScope(currentUser);
        return findFollowUpUsersNotLoggedInBy11Am(currentUser, scope).size();
    }

    @Override
    @Transactional(readOnly = true)
    public com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInPageResponseDTO getFollowUpUsersNotLoggedInBy11Am(com.app.datadistribution.common.PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException {
        User currentUser = getCurrentUserEntity();
        DataScope scope = resolveDataScope(currentUser);

        List<com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInSummaryDTO> allItems = findFollowUpUsersNotLoggedInBy11Am(currentUser, scope);

        if (pageRequest == null) {
            pageRequest = new com.app.datadistribution.common.PageRequestDTO();
        }

        String search = pageRequest.getSearch();
        if (search != null && !search.isBlank()) {
            String searchPattern = search.toLowerCase();
            allItems = allItems.stream()
                    .filter(u -> (u.getName() != null && u.getName().toLowerCase().contains(searchPattern))
                            || (u.getUsername() != null && u.getUsername().toLowerCase().contains(searchPattern))
                            || (u.getEmail() != null && u.getEmail().toLowerCase().contains(searchPattern)))
                    .collect(Collectors.toList());
        }

        String sortBy = pageRequest.getSortBy();
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "todayFollowUpCount";
        }
        boolean isAsc = pageRequest.getSortDirection() == null || !pageRequest.getSortDirection().equalsIgnoreCase("DESC");

        Comparator<com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInSummaryDTO> comparator;
        switch (sortBy) {
            case "earliestFollowUpTime":
                comparator = Comparator.comparing(com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInSummaryDTO::getEarliestFollowUpTime, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "name":
                comparator = Comparator.comparing(com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInSummaryDTO::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "todayFollowUpCount":
            default:
                comparator = Comparator.comparingLong(com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInSummaryDTO::getTodayFollowUpCount);
                break;
        }

        if (!isAsc) {
            comparator = comparator.reversed();
        }
        allItems.sort(comparator);

        int page = Math.max(0, pageRequest.getPage());
        int size = pageRequest.getSize() > 0 ? pageRequest.getSize() : 20;
        int totalElements = allItems.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        int start = Math.min(page * size, totalElements);
        int end = Math.min(start + size, totalElements);
        List<com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInSummaryDTO> pagedContent = allItems.subList(start, end);

        return com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInPageResponseDTO.builder()
                .content(pagedContent)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(page >= totalPages - 1)
                .build();
    }

    private List<com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInSummaryDTO> findFollowUpUsersNotLoggedInBy11Am(User currentUser, DataScope scope) {
        ZonedDateTime nowIST = ZonedDateTime.now(IST_ZONE);
        LocalTime cutoff = parseCutoffTime();

        if (nowIST.toLocalTime().isBefore(cutoff)) {
            return Collections.emptyList();
        }

        LocalDateTime startOfDayIST = nowIST.toLocalDate().atStartOfDay();
        LocalDateTime endOfDayIST = nowIST.toLocalDate().atTime(LocalTime.MAX);
        LocalDateTime cutoffDateTimeIST = nowIST.toLocalDate().atTime(cutoff);

        List<User> activeUsers = getActiveMonitoredUsersInScope(currentUser, scope);

        List<Object[]> followUpCounts = leadFollowUpRepository.countScheduledFollowUpsGroupedByUserBetween(startOfDayIST, endOfDayIST);
        Map<UUID, Long> countMap = new HashMap<>();
        for (Object[] row : followUpCounts) {
            UUID uId = (UUID) row[0];
            Long count = (Long) row[1];
            if (uId != null) countMap.put(uId, count);
        }

        List<Object[]> earliestTimes = leadFollowUpRepository.findEarliestScheduledFollowUpGroupedByUserBetween(startOfDayIST, endOfDayIST);
        Map<UUID, LocalDateTime> earliestMap = new HashMap<>();
        for (Object[] row : earliestTimes) {
            UUID uId = (UUID) row[0];
            LocalDateTime time = (LocalDateTime) row[1];
            if (uId != null) earliestMap.put(uId, time);
        }

        List<Object[]> loginStats = activityLogRepository.findDailyLoginStatsGroupedByPerformedBy(startOfDayIST, endOfDayIST);
        Map<String, LocalDateTime> firstLoginTodayMap = new HashMap<>();
        for (Object[] row : loginStats) {
            String perf = (String) row[0];
            LocalDateTime minCreated = (LocalDateTime) row[1];
            if (perf != null && minCreated != null) {
                firstLoginTodayMap.put(perf.toLowerCase(), minCreated);
            }
        }

        List<com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInSummaryDTO> result = new ArrayList<>();
        for (User u : activeUsers) {
            long followUpCount = countMap.getOrDefault(u.getId(), 0L);
            if (followUpCount > 0) {
                LocalDateTime firstLoginToday = null;
                if (u.getEmail() != null && firstLoginTodayMap.containsKey(u.getEmail().toLowerCase())) {
                    firstLoginToday = firstLoginTodayMap.get(u.getEmail().toLowerCase());
                } else if (u.getUsername() != null && firstLoginTodayMap.containsKey(u.getUsername().toLowerCase())) {
                    firstLoginToday = firstLoginTodayMap.get(u.getUsername().toLowerCase());
                } else if (u.getLastLogin() != null && !u.getLastLogin().isBefore(startOfDayIST)) {
                    firstLoginToday = u.getLastLogin();
                }

                boolean missedCutoff = (firstLoginToday == null) || firstLoginToday.isAfter(cutoffDateTimeIST);

                if (missedCutoff) {
                    List<String> roles = u.getRoles() != null
                            ? u.getRoles().stream().map(com.app.datadistribution.entity.Role::getName).collect(Collectors.toList())
                            : Collections.emptyList();
                    List<String> depts = u.getDepartments() != null
                            ? u.getDepartments().stream().map(com.app.datadistribution.entity.Department::getName).collect(Collectors.toList())
                            : Collections.emptyList();

                    result.add(com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInSummaryDTO.builder()
                            .userId(u.getId())
                            .name(u.getFirstName() + " " + u.getLastName())
                            .username(u.getUsername())
                            .email(u.getEmail())
                            .roleNames(roles)
                            .departmentNames(depts)
                            .todayFollowUpCount(followUpCount)
                            .earliestFollowUpTime(earliestMap.get(u.getId()))
                            .lastSuccessfulLoginAt(u.getLastLogin())
                            .firstLoginTodayAt(firstLoginToday)
                            .missedLoginCutoff(true)
                            .build());
                }
            }
        }
        return result;
    }
}
