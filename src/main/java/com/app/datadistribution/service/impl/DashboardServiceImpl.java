package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.dashboard.*;
import com.app.datadistribution.entity.*;
import com.app.datadistribution.enums.DashboardGroupBy;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.*;
import com.app.datadistribution.service.interfaces.IDashboardService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final EntityManager entityManager;

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
                default:
                    card.setValue(0);
                    break;
            }
        }
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
}
