package com.app.datadistribution.service.impl;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.user.UserPerformanceFilterRequest;
import com.app.datadistribution.dto.user.UserPerformancePageResponse;
import com.app.datadistribution.dto.user.UserPerformanceResponse;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.ActivityPeriodType;
import com.app.datadistribution.enums.LogoutReason;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.enums.SessionStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import com.app.datadistribution.service.interfaces.IUserPerformanceService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPerformanceServiceImpl implements IUserPerformanceService {

    private final UserRepository userRepository;
    private final LeadStatusRepository leadStatusRepository;
    private final DepartmentRepository departmentRepository;
    private final IUserDataScopeService dataScopeService;

    @PersistenceContext
    private final EntityManager entityManager;

    private static final int AUTO_LOGOUT_THRESHOLD_MINUTES = 15;



    public static class TimeInterval {
        private final LocalDateTime start;
        private LocalDateTime end;

        public TimeInterval(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        public LocalDateTime getStart() { return start; }
        public LocalDateTime getEnd() { return end; }
    }

    public static List<TimeInterval> mergeIntervals(List<TimeInterval> intervals) {
        if (intervals == null || intervals.isEmpty()) return Collections.emptyList();
        intervals.sort(Comparator.comparing(TimeInterval::getStart));
        List<TimeInterval> merged = new ArrayList<>();
        TimeInterval current = new TimeInterval(intervals.get(0).getStart(), intervals.get(0).getEnd());
        for (int i = 1; i < intervals.size(); i++) {
            TimeInterval next = intervals.get(i);
            if (!next.getStart().isAfter(current.getEnd())) {
                if (next.getEnd().isAfter(current.getEnd())) {
                    current.end = next.getEnd();
                }
            } else {
                merged.add(current);
                current = new TimeInterval(next.getStart(), next.getEnd());
            }
        }
        merged.add(current);
        return merged;
    }

    public static class ResolvedDateRange {
        private final String period;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final LocalDateTime startOfPeriod;
        private final LocalDateTime endOfPeriod;

        public ResolvedDateRange(String period, LocalDate startDate, LocalDate endDate, LocalDateTime startOfPeriod, LocalDateTime endOfPeriod) {
            this.period = period;
            this.startDate = startDate;
            this.endDate = endDate;
            this.startOfPeriod = startOfPeriod;
            this.endOfPeriod = endOfPeriod;
        }

        public String getPeriod() { return period; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public LocalDateTime getStartOfPeriod() { return startOfPeriod; }
        public LocalDateTime getEndOfPeriod() { return endOfPeriod; }
    }

    private ResolvedDateRange resolveDateRange(UserPerformanceFilterRequest filterRequest) throws BadRequestException {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(zone);

        String period = filterRequest != null && filterRequest.getPeriod() != null && !filterRequest.getPeriod().isBlank()
                ? filterRequest.getPeriod().trim().toUpperCase() : null;
        LocalDate reqStart = filterRequest != null ? filterRequest.getStartDate() : null;
        LocalDate reqEnd = filterRequest != null ? filterRequest.getEndDate() : null;

        if (period == null) {
            if (reqStart != null || reqEnd != null) {
                period = "CUSTOM";
            } else {
                period = "TODAY";
            }
        }

        LocalDate start;
        LocalDate end;

        switch (period) {
            case "TODAY":
                start = today;
                end = today;
                break;
            case "YESTERDAY":
                start = today.minusDays(1);
                end = today.minusDays(1);
                break;
            case "THIS_WEEK":
                start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                break;
            case "THIS_MONTH":
                start = today.withDayOfMonth(1);
                end = today.with(TemporalAdjusters.lastDayOfMonth());
                break;
            case "THIS_YEAR":
                start = today.withDayOfYear(1);
                end = today.with(TemporalAdjusters.lastDayOfYear());
                break;
            case "CUSTOM":
                if (reqStart == null && reqEnd == null) {
                    log.info("CUSTOM period requested without dates; defaulting to TODAY");
                    start = today;
                    end = today;
                    period = "TODAY";
                } else if (reqStart == null || reqEnd == null) {
                    throw new BadRequestException("Both startDate and endDate are required for CUSTOM date range");
                } else {
                    if (reqStart.isAfter(reqEnd)) {
                        throw new BadRequestException("startDate cannot be after endDate");
                    }
                    start = reqStart;
                    end = reqEnd;
                }
                break;
            default:
                throw new BadRequestException("Invalid period filter: " + period + ". Allowed values: TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, CUSTOM");
        }

        LocalDateTime startOfPeriod = start.atStartOfDay();
        LocalDateTime endOfPeriod = end.plusDays(1).atStartOfDay();

        return new ResolvedDateRange(period, start, end, startOfPeriod, endOfPeriod);
    }

    @Override
    @Transactional(readOnly = true)
    public UserPerformancePageResponse getUserPerformance(UserPerformanceFilterRequest filterRequest)
            throws UnauthorizedException, BadRequestException {

        // 0. Resolve and Validate Date Range
        ResolvedDateRange dateRange = resolveDateRange(filterRequest);

        // 1. Resolve User Data Scope
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        if (dataScope == null) {
            throw new UnauthorizedException("User data scope could not be determined");
        }

        // 2. Fetch candidate users according to authorization scope
        List<User> candidateUsers = getAuthorizedOperationalUsers(dataScope);

        // 3. Apply Filters (Department, Role, Search, Status)
        if (filterRequest != null) {
            // Department Filter (strictly subordinate to HOD scope)
            if (filterRequest.getDepartmentId() != null) {
                UUID reqDeptId = filterRequest.getDepartmentId();
                if (dataScope.getScopeType() == ScopeType.DEPARTMENT) {
                    Set<UUID> allowedDeptIds = dataScope.getDepartmentIds() != null ? dataScope.getDepartmentIds() : Collections.emptySet();
                    if (!allowedDeptIds.contains(reqDeptId)) {
                        candidateUsers = Collections.emptyList();
                    } else {
                        candidateUsers = candidateUsers.stream()
                                .filter(u -> u.getDepartments() != null && u.getDepartments().stream().anyMatch(d -> reqDeptId.equals(d.getId())))
                                .collect(Collectors.toList());
                    }
                } else if (dataScope.getScopeType() == ScopeType.SELF) {
                    // Counselor cannot change scope via departmentId
                    UUID selfUserId = dataScope.getUserId();
                    candidateUsers = candidateUsers.stream()
                            .filter(u -> u.getId().equals(selfUserId) && u.getDepartments() != null && u.getDepartments().stream().anyMatch(d -> reqDeptId.equals(d.getId())))
                            .collect(Collectors.toList());
                } else {
                    candidateUsers = candidateUsers.stream()
                            .filter(u -> u.getDepartments() != null && u.getDepartments().stream().anyMatch(d -> reqDeptId.equals(d.getId())))
                            .collect(Collectors.toList());
                }
            }

            // Role Filter
            List<String> targetRoles = new ArrayList<>();
            if (filterRequest.getRole() != null && !filterRequest.getRole().isBlank()) {
                targetRoles.add(filterRequest.getRole().trim());
            }
            if (filterRequest.getRoles() != null && !filterRequest.getRoles().isEmpty()) {
                targetRoles.addAll(filterRequest.getRoles());
            }
            if (!targetRoles.isEmpty()) {
                candidateUsers = candidateUsers.stream()
                        .filter(u -> u.getRoles() != null && u.getRoles().stream().anyMatch(r ->
                                targetRoles.stream().anyMatch(tr -> tr.equalsIgnoreCase(r.getName()) || r.getName().toUpperCase().contains(tr.toUpperCase()))
                        ))
                        .collect(Collectors.toList());
            }

            // Status Filter (ACTIVE / INACTIVE)
            if (filterRequest.getStatus() != null && !filterRequest.getStatus().isBlank() && !"ALL".equalsIgnoreCase(filterRequest.getStatus())) {
                boolean activeFilter = "ACTIVE".equalsIgnoreCase(filterRequest.getStatus()) || "true".equalsIgnoreCase(filterRequest.getStatus());
                candidateUsers = candidateUsers.stream()
                        .filter(u -> u.isActive() == activeFilter)
                        .collect(Collectors.toList());
            }

            // Search Filter
            if (filterRequest.getSearch() != null && !filterRequest.getSearch().isBlank()) {
                String searchPattern = filterRequest.getSearch().trim().toLowerCase();
                candidateUsers = candidateUsers.stream()
                        .filter(u -> {
                            String fullName = ((u.getFirstName() != null ? u.getFirstName() : "") + " " +
                                    (u.getLastName() != null ? u.getLastName() : "")).trim().toLowerCase();
                            String username = u.getUsername() != null ? u.getUsername().toLowerCase() : "";
                            String email = u.getEmail() != null ? u.getEmail().toLowerCase() : "";
                            String phone = u.getPhone() != null ? u.getPhone().toLowerCase() : "";
                            return fullName.contains(searchPattern) || username.contains(searchPattern)
                                    || email.contains(searchPattern) || phone.contains(searchPattern);
                        })
                        .collect(Collectors.toList());
            }
        }

        if (candidateUsers.isEmpty()) {
            int page = filterRequest != null && filterRequest.getPage() != null && filterRequest.getPage() >= 0 ? filterRequest.getPage() : 0;
            int size = filterRequest != null && filterRequest.getSize() != null && filterRequest.getSize() > 0 ? filterRequest.getSize() : 10;
            return UserPerformancePageResponse.builder()
                    .content(Collections.emptyList())
                    .page(page)
                    .size(size)
                    .totalElements(0L)
                    .totalPages(0)
                    .last(true)
                    .period(dateRange.getPeriod())
                    .startDate(dateRange.getStartDate())
                    .endDate(dateRange.getEndDate())
                    .build();
        }

        // 4. Resolve Dynamic Lead Status IDs (RAW, REGISTERED, CONNECTED)
        Set<UUID> rawStatusIds = getDescendantStatusIds("RAW");
        Set<UUID> registeredStatusIds = getDescendantStatusIds("REGISTERED");
        Set<UUID> connectedStatusIds = getDescendantStatusIds("CONNECTED");

        Set<UUID> candidateUserIds = candidateUsers.stream().map(User::getId).collect(Collectors.toSet());
        LocalDateTime startOfPeriod = dateRange.getStartOfPeriod();
        LocalDateTime endOfPeriod = dateRange.getEndOfPeriod();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

        // 5. Independent Grouped Aggregations by User ID
        Map<UUID, LeadStatsDTO> leadStatsMap = fetchLeadStatsForUsers(candidateUserIds, rawStatusIds, registeredStatusIds, connectedStatusIds, startOfPeriod, endOfPeriod);
        Map<UUID, FollowupStatsDTO> followupStatsMap = fetchFollowupStatsForUsers(candidateUserIds, startOfPeriod, endOfPeriod, now);
        Map<UUID, ActivityStatsDTO> activityStatsMap = fetchActivityStatsForUsers(candidateUserIds, startOfPeriod, endOfPeriod, now);

        // 6. Assemble Row Responses
        List<UserPerformanceResponse> rows = new ArrayList<>(candidateUsers.size());
        for (User user : candidateUsers) {
            UUID uid = user.getId();
            LeadStatsDTO leadStats = leadStatsMap.getOrDefault(uid, new LeadStatsDTO());
            FollowupStatsDTO followupStats = followupStatsMap.getOrDefault(uid, new FollowupStatsDTO());
            ActivityStatsDTO activityStats = activityStatsMap.getOrDefault(uid, new ActivityStatsDTO());

            String fullName = ((user.getFirstName() != null ? user.getFirstName() : "") + " " +
                    (user.getLastName() != null ? user.getLastName() : "")).trim();
            if (fullName.isEmpty()) fullName = user.getUsername();

            List<String> roleNames = user.getRoles() != null
                    ? user.getRoles().stream().map(Role::getName).collect(Collectors.toList())
                    : Collections.emptyList();

            String departmentName = user.getDepartments() != null && !user.getDepartments().isEmpty()
                    ? user.getDepartments().stream().map(Department::getName).collect(Collectors.joining(", "))
                    : "—";

            rows.add(UserPerformanceResponse.builder()
                    .userId(uid)
                    .userName(fullName)
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .roles(roleNames)
                    .department(departmentName)
                    .active(user.isActive())
                    .status(user.isActive() ? "ACTIVE" : "INACTIVE")
                    .totalAllottedData(leadStats.totalAllotted)
                    .totalAvailedData(leadStats.totalAvailed)
                    .rawDataCount(leadStats.totalRaw)
                    .registeredDataCount(leadStats.totalRegistered)
                    .todayFollowupsCount(followupStats.totalFollowups)
                    .todayFollowupsScheduled(followupStats.totalScheduled)
                    .todayMissedFollowups(followupStats.totalMissed)
                    .todayUpcomingFollowups(followupStats.totalUpcoming)
                    .todayPendingFollowups(followupStats.totalPending)
                    .todayConnectedCalls(leadStats.totalConnected)
                    .currentlyWorking(activityStats.currentlyWorking)
                    .todayLoginCount(activityStats.loginCount)
                    .todayLogoutCount(activityStats.logoutCount)
                    .todayWorkingHours(activityStats.workingHours)
                    .build());
        }

        // 7. Filter by Currently Working (if requested)
        if (filterRequest != null && filterRequest.getCurrentlyWorking() != null) {
            boolean targetWorking = filterRequest.getCurrentlyWorking();
            rows = rows.stream()
                    .filter(r -> Boolean.valueOf(targetWorking).equals(r.getCurrentlyWorking()))
                    .collect(Collectors.toList());
        }

        // 8. Server-Side Sorting
        String sortBy = filterRequest != null && filterRequest.getSortBy() != null ? filterRequest.getSortBy().trim() : "userName";
        String sortDir = filterRequest != null && "DESC".equalsIgnoreCase(filterRequest.getSortDirection()) ? "DESC" : "ASC";
        sortRows(rows, sortBy, sortDir);

        // 9. Server-Side Pagination
        int page = filterRequest != null && filterRequest.getPage() != null && filterRequest.getPage() >= 0 ? filterRequest.getPage() : 0;
        int size = filterRequest != null && filterRequest.getSize() != null && filterRequest.getSize() > 0 ? filterRequest.getSize() : 10;
        long totalElements = rows.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        int fromIndex = page * size;
        List<UserPerformanceResponse> pagedContent;
        if (fromIndex >= rows.size()) {
            pagedContent = Collections.emptyList();
        } else {
            int toIndex = Math.min(fromIndex + size, rows.size());
            pagedContent = rows.subList(fromIndex, toIndex);
        }

        return UserPerformancePageResponse.builder()
                .content(pagedContent)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last((page + 1) >= totalPages)
                .period(dateRange.getPeriod())
                .startDate(dateRange.getStartDate())
                .endDate(dateRange.getEndDate())
                .build();
    }

    private List<User> getAuthorizedOperationalUsers(UserDataScope dataScope) {
        if (dataScope.getScopeType() == ScopeType.SELF) {
            User current = dataScope.getCurrentUser();
            if (current != null && current.isActive() && !current.isDeleted() && isEligibleOperationalUser(current)) {
                return new ArrayList<>(List.of(current));
            }
            User userFromDb = userRepository.findById(dataScope.getUserId()).orElse(null);
            if (userFromDb != null && userFromDb.isActive() && !userFromDb.isDeleted() && isEligibleOperationalUser(userFromDb)) {
                return new ArrayList<>(List.of(userFromDb));
            }
            return Collections.emptyList();
        }

        List<User> allOperational = userRepository.findAll().stream()
                .filter(u -> !u.isDeleted() && isEligibleOperationalUser(u))
                .collect(Collectors.toList());

        if (dataScope.getScopeType() == ScopeType.DEPARTMENT) {
            Set<UUID> allowedUserIds = dataScope.getDepartmentUserIds() != null ? dataScope.getDepartmentUserIds() : Collections.emptySet();
            Set<UUID> allowedDeptIds = dataScope.getDepartmentIds() != null ? dataScope.getDepartmentIds() : Collections.emptySet();
            return allOperational.stream()
                    .filter(u -> allowedUserIds.contains(u.getId())
                            || (u.getDepartments() != null && u.getDepartments().stream().anyMatch(d -> allowedDeptIds.contains(d.getId()))))
                    .collect(Collectors.toList());
        }

        return allOperational;
    }

    private boolean isEligibleOperationalUser(User user) {
        if (user == null || user.getRoles() == null || user.getRoles().isEmpty()) {
            return false;
        }
        boolean hasAdminRole = user.getRoles().stream()
                .anyMatch(r -> r.getName() != null && (
                        r.getName().equalsIgnoreCase(RoleType.SUPER_ADMIN.name())
                        || r.getName().equalsIgnoreCase(RoleType.ADMIN.name())
                        || r.getName().equalsIgnoreCase("SUPER_ADMIN")
                        || r.getName().equalsIgnoreCase("ADMIN")
                        || r.getName().equalsIgnoreCase("ROLE_SUPER_ADMIN")
                        || r.getName().equalsIgnoreCase("ROLE_ADMIN")
                ));
        if (hasAdminRole) {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(r -> {
                    if (r.getName() == null) return false;
                    String upper = r.getName().toUpperCase();
                    return upper.equals(RoleType.COUNSELOR.name())
                            || upper.equals(RoleType.HOD.name())
                            || upper.equals(RoleType.USER.name())
                            || upper.contains("COUNSELOR")
                            || upper.contains("COUNSELLOR")
                            || upper.contains("HOD")
                            || upper.contains("HEAD");
                });
    }

    /**
     * Grouped lead stats query by assigned user ID
     */
    @SuppressWarnings("unchecked")
    private Map<UUID, LeadStatsDTO> fetchLeadStatsForUsers(Set<UUID> userIds, Set<UUID> rawStatusIds, Set<UUID> registeredStatusIds, Set<UUID> connectedStatusIds, LocalDateTime startOfPeriod, LocalDateTime endOfPeriod) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
           .append("  l.assigned_to_id AS user_id, ")
           .append("  COUNT(DISTINCT l.id) AS total_allotted, ")
           .append("  COUNT(DISTINCT CASE WHEN EXISTS (SELECT 1 FROM lead_availed la WHERE la.lead_id = l.id AND la.availed_by_user_id = l.assigned_to_id AND la.is_deleted = false) THEN l.id END) AS total_availed ");

        if (!rawStatusIds.isEmpty()) {
            sql.append(", COUNT(DISTINCT CASE WHEN l.lead_status_id IN (:rawStatusIds) THEN l.id END) AS total_raw ");
        } else {
            sql.append(", 0 AS total_raw ");
        }

        if (!registeredStatusIds.isEmpty()) {
            sql.append(", COUNT(DISTINCT CASE WHEN l.lead_status_id IN (:registeredStatusIds) THEN l.id END) AS total_registered ");
        } else {
            sql.append(", 0 AS total_registered ");
        }

        if (!connectedStatusIds.isEmpty()) {
            sql.append(", COUNT(DISTINCT CASE WHEN l.lead_status_id IN (:connectedStatusIds) AND (");
            sql.append("  EXISTS (SELECT 1 FROM lead_status_histories lsh WHERE lsh.lead_id = l.id AND lsh.new_status_id IN (:connectedStatusIds) AND (lsh.changed_by_user_id = l.assigned_to_id OR lsh.changed_by_user_id IS NULL) AND lsh.created_at >= :startOfPeriod AND lsh.created_at < :endOfPeriod) ");
            sql.append("  OR (l.updated_at >= :startOfPeriod AND l.updated_at < :endOfPeriod)");
            sql.append(") THEN l.id END) AS total_connected ");
        } else {
            sql.append(", 0 AS total_connected ");
        }

        sql.append("FROM leads l ")
           .append("WHERE l.is_deleted = false AND l.assigned_to_id IN (:userIds) ")
           .append("GROUP BY l.assigned_to_id");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("userIds", userIds);
        if (!rawStatusIds.isEmpty()) {
            query.setParameter("rawStatusIds", rawStatusIds);
        }
        if (!registeredStatusIds.isEmpty()) {
            query.setParameter("registeredStatusIds", registeredStatusIds);
        }
        if (!connectedStatusIds.isEmpty()) {
            query.setParameter("connectedStatusIds", connectedStatusIds);
            query.setParameter("startOfPeriod", startOfPeriod);
            query.setParameter("endOfPeriod", endOfPeriod);
        }

        List<?> results = query.getResultList();
        Map<UUID, LeadStatsDTO> map = new HashMap<>();

        for (Object item : results) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID uid = parseUUID(row[0]);
            if (uid != null) {
                LeadStatsDTO dto = new LeadStatsDTO();
                dto.totalAllotted = parseLong(row[1]);
                dto.totalAvailed = parseLong(row[2]);
                dto.totalRaw = parseLong(row[3]);
                dto.totalRegistered = parseLong(row[4]);
                dto.totalConnected = parseLong(row[5]);
                map.put(uid, dto);
            }
        }
        return map;
    }

    /**
     * Grouped followup stats query by user ID for Today
     */
    @SuppressWarnings("unchecked")
    private Map<UUID, FollowupStatsDTO> fetchFollowupStatsForUsers(Set<UUID> userIds, LocalDateTime startOfPeriod, LocalDateTime endOfPeriod, LocalDateTime now) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();

        String sql = "SELECT " +
                "  lfu.assigned_to_user_id AS user_id, " +
                "  COUNT(DISTINCT lfu.id) AS total_followups, " +
                "  COUNT(DISTINCT lfu.id) AS total_scheduled, " +
                "  COUNT(DISTINCT CASE WHEN lfu.follow_up_date < :now AND lfu.completed = false THEN lfu.id END) AS total_missed, " +
                "  COUNT(DISTINCT CASE WHEN lfu.follow_up_date >= :now AND lfu.completed = false THEN lfu.id END) AS total_upcoming, " +
                "  COUNT(DISTINCT CASE WHEN lfu.completed = false THEN lfu.id END) AS total_pending " +
                "FROM lead_follow_ups lfu " +
                "WHERE lfu.is_deleted = false AND lfu.assigned_to_user_id IN (:userIds) " +
                "  AND lfu.follow_up_date >= :startOfPeriod AND lfu.follow_up_date < :endOfPeriod " +
                "GROUP BY lfu.assigned_to_user_id";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userIds", userIds);
        query.setParameter("startOfPeriod", startOfPeriod);
        query.setParameter("endOfPeriod", endOfPeriod);
        query.setParameter("now", now);

        List<?> results = query.getResultList();
        Map<UUID, FollowupStatsDTO> map = new HashMap<>();

        for (Object item : results) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID uid = parseUUID(row[0]);
            if (uid != null) {
                FollowupStatsDTO dto = new FollowupStatsDTO();
                dto.totalFollowups = parseLong(row[1]);
                dto.totalScheduled = parseLong(row[2]);
                dto.totalMissed = parseLong(row[3]);
                dto.totalUpcoming = parseLong(row[4]);
                dto.totalPending = parseLong(row[5]);
                map.put(uid, dto);
            }
        }
        return map;
    }

    /**
     * Grouped activity and session stats query by user ID
     */
    @SuppressWarnings("unchecked")
    private Map<UUID, ActivityStatsDTO> fetchActivityStatsForUsers(Set<UUID> userIds, LocalDateTime startOfPeriod, LocalDateTime endOfPeriod, LocalDateTime now) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();

        // 1. Fetch sessions overlapping the period or currently active
        String sessionSql = "SELECT uls.id, uls.user_id, uls.login_at, uls.logout_at, uls.last_activity_at, uls.session_status, uls.total_inactive_duration_seconds, uls.logout_reason " +
                "FROM user_login_sessions uls " +
                "WHERE uls.user_id IN (:userIds) AND uls.is_deleted = false " +
                "  AND (" +
                "    (uls.login_at < :endOfPeriod AND (uls.logout_at IS NULL OR uls.logout_at >= :startOfPeriod)) " +
                "    OR (uls.logout_at IS NULL AND uls.session_status = 'ACTIVE')" +
                "  ) " +
                "ORDER BY uls.login_at ASC";

        Query sessionQuery = entityManager.createNativeQuery(sessionSql);
        sessionQuery.setParameter("userIds", userIds);
        sessionQuery.setParameter("startOfPeriod", startOfPeriod);
        sessionQuery.setParameter("endOfPeriod", endOfPeriod);

        List<?> sessionRows = sessionQuery.getResultList();

        // 2. Fetch inactive periods overlapping the period
        String periodSql = "SELECT uap.user_id, uap.started_at, uap.ended_at, uap.duration_seconds " +
                "FROM user_activity_periods uap " +
                "WHERE uap.user_id IN (:userIds) AND uap.is_deleted = false AND uap.period_type = 'INACTIVE' " +
                "  AND uap.started_at < :endOfPeriod AND (uap.ended_at IS NULL OR uap.ended_at >= :startOfPeriod)";

        Query periodQuery = entityManager.createNativeQuery(periodSql);
        periodQuery.setParameter("userIds", userIds);
        periodQuery.setParameter("startOfPeriod", startOfPeriod);
        periodQuery.setParameter("endOfPeriod", endOfPeriod);

        List<?> periodRows = periodQuery.getResultList();

        Map<UUID, ActivityStatsDTO> map = new HashMap<>();
        Map<UUID, List<TimeInterval>> userSessionIntervals = new HashMap<>();
        Map<UUID, List<TimeInterval>> userInactiveIntervals = new HashMap<>();
        Map<UUID, LocalDateTime> userLastLoginTime = new HashMap<>();
        LocalDateTime fifteenMinAgo = now.minusMinutes(AUTO_LOGOUT_THRESHOLD_MINUTES);

        for (Object s : sessionRows) {
            Object[] row = s instanceof Object[] ? (Object[]) s : new Object[]{s};
            UUID uid = parseUUID(row[1]);
            if (uid == null) continue;

            ActivityStatsDTO dto = map.computeIfAbsent(uid, k -> new ActivityStatsDTO());
            LocalDateTime loginAt = parseLocalDateTime(row[2]);
            LocalDateTime logoutAt = parseLocalDateTime(row[3]);
            LocalDateTime lastAct = parseLocalDateTime(row[4]);
            String sessionStatus = row[5] != null ? row[5].toString() : "ACTIVE";
            String logoutReason = row.length > 7 && row[7] != null ? row[7].toString() : null;

            if (loginAt == null) continue;

            // Real-time presence check: active session with recent activity within 15 min
            boolean isLiveActive = (logoutAt == null)
                    && "ACTIVE".equalsIgnoreCase(sessionStatus)
                    && (lastAct != null && !lastAct.isBefore(fifteenMinAgo));

            if (isLiveActive) {
                dto.currentlyWorking = true;
            }

            // Determine bounded session end time
            LocalDateTime sessionEnd;
            if (isLiveActive) {
                sessionEnd = now;
            } else if (logoutAt != null) {
                sessionEnd = logoutAt;
            } else {
                // Stale or unclosed session: activity ceased at lastActivityAt
                sessionEnd = (lastAct != null && lastAct.isAfter(loginAt)) ? lastAct : loginAt;
            }

            if (sessionEnd.isBefore(loginAt)) {
                sessionEnd = loginAt;
            }

            // Clip session duration to requested period [startOfPeriod, endOfPeriod)
            LocalDateTime clipStart = loginAt.isBefore(startOfPeriod) ? startOfPeriod : loginAt;
            LocalDateTime clipEnd = sessionEnd.isAfter(endOfPeriod) ? endOfPeriod : sessionEnd;

            if (clipEnd.isAfter(clipStart)) {
                userSessionIntervals.computeIfAbsent(uid, k -> new ArrayList<>()).add(new TimeInterval(clipStart, clipEnd));
            }

            // Login count within [startOfPeriod, endOfPeriod)
            // Deduplicate rapid retries / multi-tab logins within 10 seconds
            if (!loginAt.isBefore(startOfPeriod) && loginAt.isBefore(endOfPeriod)) {
                LocalDateTime prevLogin = userLastLoginTime.get(uid);
                if (prevLogin == null || Duration.between(prevLogin, loginAt).abs().toSeconds() >= 10) {
                    dto.loginCount++;
                    userLastLoginTime.put(uid, loginAt);
                }
            }

            // Logout count within [startOfPeriod, endOfPeriod)
            // Count actual user logout events; exclude internal SYSTEM_LOGOUT cleanups
            if (logoutAt != null && !logoutAt.isBefore(startOfPeriod) && logoutAt.isBefore(endOfPeriod)) {
                if (logoutReason == null || !LogoutReason.SYSTEM_LOGOUT.name().equalsIgnoreCase(logoutReason)) {
                    dto.logoutCount++;
                }
            }
        }

        // Process inactive periods clipped to [startOfPeriod, endOfPeriod)
        for (Object p : periodRows) {
            Object[] row = p instanceof Object[] ? (Object[]) p : new Object[]{p};
            UUID uid = parseUUID(row[0]);
            if (uid == null) continue;

            LocalDateTime pStart = parseLocalDateTime(row[1]);
            LocalDateTime pEnd = parseLocalDateTime(row[2]);
            if (pStart == null) continue;

            LocalDateTime effectiveEnd = pEnd != null ? pEnd : now;
            if (effectiveEnd.isBefore(pStart)) effectiveEnd = pStart;

            LocalDateTime clipPStart = pStart.isBefore(startOfPeriod) ? startOfPeriod : pStart;
            LocalDateTime clipPEnd = effectiveEnd.isAfter(endOfPeriod) ? endOfPeriod : effectiveEnd;

            if (clipPEnd.isAfter(clipPStart)) {
                userInactiveIntervals.computeIfAbsent(uid, k -> new ArrayList<>()).add(new TimeInterval(clipPStart, clipPEnd));
            }
        }

        // Calculate non-overlapping net working hours for each user
        for (UUID uid : userIds) {
            ActivityStatsDTO dto = map.computeIfAbsent(uid, k -> new ActivityStatsDTO());
            List<TimeInterval> sessionList = userSessionIntervals.getOrDefault(uid, Collections.emptyList());
            List<TimeInterval> mergedSessions = mergeIntervals(sessionList);

            long totalActiveSecs = 0;
            for (TimeInterval ti : mergedSessions) {
                totalActiveSecs += Math.max(0, Duration.between(ti.getStart(), ti.getEnd()).toSeconds());
            }

            List<TimeInterval> inactiveList = userInactiveIntervals.getOrDefault(uid, Collections.emptyList());
            List<TimeInterval> mergedInactives = mergeIntervals(inactiveList);

            long totalInactiveSecs = 0;
            for (TimeInterval ti : mergedInactives) {
                totalInactiveSecs += Math.max(0, Duration.between(ti.getStart(), ti.getEnd()).toSeconds());
            }

            long netWorkingSecs = Math.max(0, totalActiveSecs - totalInactiveSecs);
            dto.workingHours = Math.round((netWorkingSecs / 3600.0) * 100.0) / 100.0;
        }

        return map;
    }

    private Set<UUID> getDescendantStatusIds(String rootCode) {
        List<LeadStatus> allStatuses = leadStatusRepository.findAll();
        Set<UUID> collected = new HashSet<>();

        Optional<LeadStatus> rootStatus = allStatuses.stream()
                .filter(s -> !s.isDeleted() && rootCode.equalsIgnoreCase(s.getCode()))
                .findFirst();

        if (rootStatus.isPresent()) {
            collected.add(rootStatus.get().getId());
            boolean addedNew;
            do {
                addedNew = false;
                for (LeadStatus s : allStatuses) {
                    if (!s.isDeleted() && !collected.contains(s.getId())) {
                        if (s.getParentStatus() != null && collected.contains(s.getParentStatus().getId())) {
                            collected.add(s.getId());
                            addedNew = true;
                        }
                    }
                }
            } while (addedNew);
        }

        for (LeadStatus s : allStatuses) {
            if (!s.isDeleted() && s.getCode() != null) {
                if (s.getCode().toUpperCase().startsWith(rootCode.toUpperCase()) ||
                    s.getCode().toUpperCase().endsWith(rootCode.toUpperCase())) {
                    collected.add(s.getId());
                }
            }
        }

        return collected;
    }

    private void sortRows(List<UserPerformanceResponse> rows, String sortBy, String sortDir) {
        Comparator<UserPerformanceResponse> comparator;
        boolean desc = "DESC".equalsIgnoreCase(sortDir);

        switch (sortBy.toLowerCase()) {
            case "username":
            case "name":
            case "firstname":
                comparator = Comparator.comparing(UserPerformanceResponse::getUserName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "email":
                comparator = Comparator.comparing(UserPerformanceResponse::getEmail, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "department":
                comparator = Comparator.comparing(UserPerformanceResponse::getDepartment, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "status":
            case "active":
                comparator = Comparator.comparing(UserPerformanceResponse::getStatus, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "totalallotteddata":
            case "totalallotted":
            case "allotted":
                comparator = Comparator.comparing(UserPerformanceResponse::getTotalAllottedData, Comparator.nullsLast(Long::compareTo));
                break;
            case "totalavaileddata":
            case "totalavailed":
            case "availed":
                comparator = Comparator.comparing(UserPerformanceResponse::getTotalAvailedData, Comparator.nullsLast(Long::compareTo));
                break;
            case "rawdatacount":
            case "raw":
                comparator = Comparator.comparing(UserPerformanceResponse::getRawDataCount, Comparator.nullsLast(Long::compareTo));
                break;
            case "registereddatacount":
            case "registered":
                comparator = Comparator.comparing(UserPerformanceResponse::getRegisteredDataCount, Comparator.nullsLast(Long::compareTo));
                break;
            case "todayfollowupscount":
            case "todayfollowups":
            case "followups":
                comparator = Comparator.comparing(UserPerformanceResponse::getTodayFollowupsCount, Comparator.nullsLast(Long::compareTo));
                break;
            case "todayfollowupsscheduled":
            case "scheduled":
                comparator = Comparator.comparing(UserPerformanceResponse::getTodayFollowupsScheduled, Comparator.nullsLast(Long::compareTo));
                break;
            case "todaymissedfollowups":
            case "missed":
                comparator = Comparator.comparing(UserPerformanceResponse::getTodayMissedFollowups, Comparator.nullsLast(Long::compareTo));
                break;
            case "todayupcomingfollowups":
            case "upcoming":
                comparator = Comparator.comparing(UserPerformanceResponse::getTodayUpcomingFollowups, Comparator.nullsLast(Long::compareTo));
                break;
            case "todaypendingfollowups":
            case "pending":
                comparator = Comparator.comparing(UserPerformanceResponse::getTodayPendingFollowups, Comparator.nullsLast(Long::compareTo));
                break;
            case "todayconnectedcalls":
            case "connectedcalls":
            case "calls":
                comparator = Comparator.comparing(UserPerformanceResponse::getTodayConnectedCalls, Comparator.nullsLast(Long::compareTo));
                break;
            case "currentlyworking":
                comparator = Comparator.comparing(UserPerformanceResponse::getCurrentlyWorking, Comparator.nullsLast(Boolean::compareTo));
                break;
            case "todaylogincount":
            case "logincount":
                comparator = Comparator.comparing(UserPerformanceResponse::getTodayLoginCount, Comparator.nullsLast(Long::compareTo));
                break;
            case "todaylogoutcount":
            case "logoutcount":
                comparator = Comparator.comparing(UserPerformanceResponse::getTodayLogoutCount, Comparator.nullsLast(Long::compareTo));
                break;
            case "todayworkinghours":
            case "workinghours":
                comparator = Comparator.comparing(UserPerformanceResponse::getTodayWorkingHours, Comparator.nullsLast(Double::compareTo));
                break;
            default:
                comparator = Comparator.comparing(UserPerformanceResponse::getUserName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
        }

        if (desc) {
            comparator = comparator.reversed();
        }
        rows.sort(comparator);
    }

    private UUID parseUUID(Object obj) {
        if (obj == null) return null;
        if (obj instanceof UUID) return (UUID) obj;
        if (obj instanceof byte[] b && b.length == 16) {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b);
            return new UUID(bb.getLong(), bb.getLong());
        }
        try {
            return UUID.fromString(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDateTime ldt) return ldt;
        if (obj instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (obj instanceof java.util.Date d) return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        try {
            return LocalDateTime.parse(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private long parseLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number num) return num.longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    private static class LeadStatsDTO {
        long totalAllotted = 0L;
        long totalAvailed = 0L;
        long totalRaw = 0L;
        long totalRegistered = 0L;
        long totalConnected = 0L;
    }

    private static class FollowupStatsDTO {
        long totalFollowups = 0L;
        long totalScheduled = 0L;
        long totalMissed = 0L;
        long totalUpcoming = 0L;
        long totalPending = 0L;
    }

    private static class ActivityStatsDTO {
        long loginCount = 0L;
        long logoutCount = 0L;
        long totalSessionSeconds = 0L;
        double workingHours = 0.0;
        boolean currentlyWorking = false;
    }
}
