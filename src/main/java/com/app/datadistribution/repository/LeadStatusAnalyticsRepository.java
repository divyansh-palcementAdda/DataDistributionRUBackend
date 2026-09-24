package com.app.datadistribution.repository;

import com.app.datadistribution.common.PageResponseDTO;
import com.app.datadistribution.dto.analytics.CourseLeadStatusRowDTO;
import com.app.datadistribution.dto.analytics.CourseStatusAnalyticsResponseDTO;
import com.app.datadistribution.dto.analytics.CourseUserStatusAnalyticsResponseDTO;
import com.app.datadistribution.dto.analytics.LeadStatusAnalyticsFilterRequest;
import com.app.datadistribution.dto.analytics.LeadStatusColumnDTO;
import com.app.datadistribution.dto.analytics.UserLeadStatusRowDTO;
import com.app.datadistribution.dto.analytics.UserStatusAnalyticsResponseDTO;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class LeadStatusAnalyticsRepository {

    private final EntityManager entityManager;
    private final LeadStatusRepository leadStatusRepository;
    private final CourseTypeRepository courseTypeRepository;

    public List<LeadStatusColumnDTO> fetchActiveStatuses() {
        return leadStatusRepository.findAll().stream()
                .filter(s -> !s.isDeleted() && s.isActive())
                .sorted(Comparator.comparing(LeadStatus::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(s -> LeadStatusColumnDTO.builder()
                        .statusId(s.getId())
                        .code(s.getCode())
                        .name(s.getName())
                        .sentimentCategory(s.getSentimentCategory() != null ? s.getSentimentCategory().name() : "NEUTRAL")
                        .displayOrder(s.getDisplayOrder())
                        .build())
                .collect(Collectors.toList());
    }

    public CourseStatusAnalyticsResponseDTO fetchCourseWiseAnalytics(
            LeadStatusAnalyticsFilterRequest filter, UserDataScope dataScope) {

        List<LeadStatusColumnDTO> statusColumns = fetchActiveStatuses();

        StringBuilder scopeClause = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        buildNativeScopeClauseAndParams(dataScope, scopeClause, params);

        StringBuilder filterClause = new StringBuilder();
        buildFilterClauseAndParams(filter, filterClause, params);

        String extraCourseTypeC1 = "";
        String extraCourseTypeC2 = "";
        if (filter.getCourseTypeId() != null) {
            extraCourseTypeC1 = " AND c1.course_type_id = :filterCourseTypeId ";
            extraCourseTypeC2 = " AND c2.course_type_id = :filterCourseTypeId ";
            params.put("filterCourseTypeId", filter.getCourseTypeId());
        }

        // 1. Identify Candidate Courses
        Map<UUID, CourseLeadStatusRowDTO> courseMap = new LinkedHashMap<>();

        if (filter.getCourseTypeId() != null) {
            StringBuilder courseSql = new StringBuilder(
                    "SELECT c.id, c.course_name, c.course_code " +
                    "FROM courses c " +
                    "WHERE c.course_type_id = :filterCourseTypeId AND c.is_deleted = false AND c.status = 'ACTIVE' ");
            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                courseSql.append("AND (LOWER(c.course_name) LIKE :courseSearch OR LOWER(c.course_code) LIKE :courseSearch) ");
                params.put("courseSearch", "%" + filter.getSearch().toLowerCase().trim() + "%");
            }
            courseSql.append("ORDER BY c.course_name ASC");

            Query candidateQuery = entityManager.createNativeQuery(courseSql.toString());
            bindParams(candidateQuery, params);
            List<?> candidates = candidateQuery.getResultList();

            for (Object item : candidates) {
                Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
                UUID cId = parseUUID(row[0]);
                if (cId != null) {
                    Map<String, Long> initialCounts = new LinkedHashMap<>();
                    for (LeadStatusColumnDTO col : statusColumns) {
                        initialCounts.put(col.getCode(), 0L);
                    }
                    CourseLeadStatusRowDTO dto = CourseLeadStatusRowDTO.builder()
                            .courseId(cId)
                            .courseName(row[1] != null ? row[1].toString() : "Unknown")
                            .courseCode(row[2] != null ? row[2].toString() : "")
                            .statusCounts(initialCounts)
                            .total(0L)
                            .build();
                    courseMap.put(cId, dto);
                }
            }
        }

        // 2. Query Status Counts for courses
        String statusCountSql =
                "SELECT " +
                "  comb.course_id, " +
                "  c.course_name, " +
                "  c.course_code, " +
                "  ls.code AS status_code, " +
                "  COUNT(DISTINCT comb.lead_id) AS cnt " +
                "FROM ( " +
                "  SELECT DISTINCT " +
                "    l.id AS lead_id, " +
                "    l.course_id AS course_id, " +
                "    l.lead_status_id AS status_id " +
                "  FROM leads l " +
                "  JOIN courses c1 ON c1.id = l.course_id AND c1.is_deleted = false " +
                "  WHERE l.is_deleted = false AND " + scopeClause + filterClause + extraCourseTypeC1 + " " +
                "  UNION " +
                "  SELECT DISTINCT " +
                "    l.id AS lead_id, " +
                "    lic.course_id AS course_id, " +
                "    l.lead_status_id AS status_id " +
                "  FROM leads l " +
                "  JOIN lead_interested_courses lic ON lic.lead_id = l.id " +
                "  JOIN courses c2 ON c2.id = lic.course_id AND c2.is_deleted = false " +
                "  WHERE l.is_deleted = false AND " + scopeClause + filterClause + extraCourseTypeC2 + " " +
                ") comb " +
                "JOIN courses c ON c.id = comb.course_id AND c.is_deleted = false " +
                "JOIN lead_statuses ls ON ls.id = comb.status_id AND ls.is_deleted = false " +
                "WHERE comb.status_id IS NOT NULL " +
                "GROUP BY comb.course_id, c.course_name, c.course_code, ls.code";

        Query countQuery = entityManager.createNativeQuery(statusCountSql);
        bindParams(countQuery, params);
        List<?> countResults = countQuery.getResultList();

        for (Object item : countResults) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID cId = parseUUID(row[0]);
            if (cId == null) continue;

            String courseName = row[1] != null ? row[1].toString() : "Unknown";
            String courseCode = row[2] != null ? row[2].toString() : "";
            String statusCode = row[3] != null ? row[3].toString() : "";
            long cnt = parseLong(row[4]);

            // If courseTypeId was null, candidate courses are derived from matching leads
            if (filter.getCourseTypeId() == null) {
                if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                    String s = filter.getSearch().toLowerCase().trim();
                    boolean matchName = courseName.toLowerCase().contains(s);
                    boolean matchCode = courseCode.toLowerCase().contains(s);
                    if (!matchName && !matchCode) {
                        continue;
                    }
                }
            }

            CourseLeadStatusRowDTO dto = courseMap.computeIfAbsent(cId, id -> {
                Map<String, Long> initialCounts = new LinkedHashMap<>();
                for (LeadStatusColumnDTO col : statusColumns) {
                    initialCounts.put(col.getCode(), 0L);
                }
                return CourseLeadStatusRowDTO.builder()
                        .courseId(id)
                        .courseName(courseName)
                        .courseCode(courseCode)
                        .statusCounts(initialCounts)
                        .total(0L)
                        .build();
            });

            if (!statusCode.isBlank()) {
                dto.getStatusCounts().put(statusCode, cnt);
            }
        }

        // 3. Compute row totals and grand totals
        Map<String, Long> grandTotalStatusCounts = new LinkedHashMap<>();
        for (LeadStatusColumnDTO col : statusColumns) {
            grandTotalStatusCounts.put(col.getCode(), 0L);
        }
        long grandTotal = 0L;

        List<CourseLeadStatusRowDTO> allRows = new ArrayList<>(courseMap.values());
        for (CourseLeadStatusRowDTO row : allRows) {
            long rowTotal = 0L;
            for (Map.Entry<String, Long> entry : row.getStatusCounts().entrySet()) {
                rowTotal += entry.getValue();
                grandTotalStatusCounts.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
            row.setTotal(rowTotal);
            grandTotal += rowTotal;
        }

        // 4. Sort rows
        sortCourseRows(allRows, filter.getEffectiveSortBy(), filter.getEffectiveSortDirection());

        // 5. Paginate
        int totalElements = allRows.size();
        int safePage = filter.getEffectivePage();
        int safeSize = filter.getEffectiveSize();
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<CourseLeadStatusRowDTO> pagedContent = (fromIndex <= toIndex)
                ? allRows.subList(fromIndex, toIndex)
                : Collections.emptyList();
        int totalPages = safeSize > 0 ? (int) Math.ceil((double) totalElements / safeSize) : 1;

        PageResponseDTO<CourseLeadStatusRowDTO> pageResponse = PageResponseDTO.<CourseLeadStatusRowDTO>builder()
                .content(pagedContent)
                .page(safePage)
                .size(safeSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last((safePage + 1) >= totalPages)
                .build();

        return CourseStatusAnalyticsResponseDTO.builder()
                .statuses(statusColumns)
                .courses(pageResponse)
                .grandTotalStatusCounts(grandTotalStatusCounts)
                .grandTotal(grandTotal)
                .build();
    }

    public UserStatusAnalyticsResponseDTO fetchUserWiseAnalytics(
            LeadStatusAnalyticsFilterRequest filter, UserDataScope dataScope) {

        List<LeadStatusColumnDTO> statusColumns = fetchActiveStatuses();

        StringBuilder scopeClause = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        buildNativeScopeClauseAndParams(dataScope, scopeClause, params);

        StringBuilder filterClause = new StringBuilder();
        buildFilterClauseAndParams(filter, filterClause, params);

        String categoryExitsClause = "";
        if (filter.getCourseTypeId() != null) {
            categoryExitsClause =
                    " AND ( " +
                    "   EXISTS (SELECT 1 FROM courses cc1 WHERE cc1.id = l.course_id AND cc1.course_type_id = :userFilterCourseTypeId AND cc1.is_deleted = false) " +
                    "   OR " +
                    "   EXISTS (SELECT 1 FROM lead_interested_courses lic JOIN courses cc2 ON cc2.id = lic.course_id WHERE lic.lead_id = l.id AND cc2.course_type_id = :userFilterCourseTypeId AND cc2.is_deleted = false) " +
                    " ) ";
            params.put("userFilterCourseTypeId", filter.getCourseTypeId());
        }

        String userSearchClause = "";
        if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
            userSearchClause = " AND (LOWER(u.first_name) LIKE :userSearch OR LOWER(u.last_name) LIKE :userSearch OR LOWER(u.username) LIKE :userSearch OR LOWER(u.email) LIKE :userSearch) ";
            params.put("userSearch", "%" + filter.getSearch().toLowerCase().trim() + "%");
        }

        String userStatusSql =
                "SELECT " +
                "  l.assigned_to_id AS user_id, " +
                "  u.first_name, " +
                "  u.last_name, " +
                "  u.username, " +
                "  u.email, " +
                "  COALESCE(MAX(r.name), '') AS role_name, " +
                "  ls.code AS status_code, " +
                "  COUNT(DISTINCT l.id) AS cnt " +
                "FROM leads l " +
                "JOIN users u ON u.id = l.assigned_to_id AND u.is_deleted = false " +
                "LEFT JOIN user_roles ur ON ur.user_id = u.id " +
                "LEFT JOIN roles r ON r.id = ur.role_id AND r.is_deleted = false " +
                "JOIN lead_statuses ls ON ls.id = l.lead_status_id AND ls.is_deleted = false " +
                "WHERE l.is_deleted = false " +
                "  AND l.assigned_to_id IS NOT NULL " +
                "  AND " + scopeClause + filterClause + categoryExitsClause + userSearchClause + " " +
                "GROUP BY l.assigned_to_id, u.first_name, u.last_name, u.username, u.email, ls.code";

        Query userQuery = entityManager.createNativeQuery(userStatusSql);
        bindParams(userQuery, params);
        List<?> userResults = userQuery.getResultList();

        Map<UUID, UserLeadStatusRowDTO> userMap = new LinkedHashMap<>();
        for (Object item : userResults) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID uId = parseUUID(row[0]);
            if (uId == null) continue;

            String firstName = row[1] != null ? row[1].toString() : "";
            String lastName = row[2] != null ? row[2].toString() : "";
            String rawName = (firstName + " " + lastName).trim();
            final String finalFullName = rawName.isEmpty() ? ("User " + uId.toString().substring(0, 6)) : rawName;

            String username = row[3] != null ? row[3].toString() : "";
            String email = row[4] != null ? row[4].toString() : "";
            String roleName = row[5] != null ? row[5].toString() : "";
            String statusCode = row[6] != null ? row[6].toString() : "";
            long cnt = parseLong(row[7]);

            UserLeadStatusRowDTO dto = userMap.computeIfAbsent(uId, id -> {
                Map<String, Long> initialCounts = new LinkedHashMap<>();
                for (LeadStatusColumnDTO col : statusColumns) {
                    initialCounts.put(col.getCode(), 0L);
                }
                return UserLeadStatusRowDTO.builder()
                        .userId(id)
                        .userName(finalFullName)
                        .username(username)
                        .email(email)
                        .role(roleName)
                        .statusCounts(initialCounts)
                        .total(0L)
                        .build();
            });

            if (!statusCode.isBlank()) {
                dto.getStatusCounts().put(statusCode, cnt);
            }
        }

        // Compute row totals and grand totals
        Map<String, Long> grandTotalStatusCounts = new LinkedHashMap<>();
        for (LeadStatusColumnDTO col : statusColumns) {
            grandTotalStatusCounts.put(col.getCode(), 0L);
        }
        long grandTotal = 0L;

        List<UserLeadStatusRowDTO> allUserRows = new ArrayList<>(userMap.values());
        for (UserLeadStatusRowDTO row : allUserRows) {
            long rowTotal = 0L;
            for (Map.Entry<String, Long> entry : row.getStatusCounts().entrySet()) {
                rowTotal += entry.getValue();
                grandTotalStatusCounts.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
            row.setTotal(rowTotal);
            grandTotal += rowTotal;
        }

        // Sort rows
        sortUserRows(allUserRows, filter.getEffectiveSortBy(), filter.getEffectiveSortDirection());

        // Paginate
        int totalElements = allUserRows.size();
        int safePage = filter.getEffectivePage();
        int safeSize = filter.getEffectiveSize();
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<UserLeadStatusRowDTO> pagedContent = (fromIndex <= toIndex)
                ? allUserRows.subList(fromIndex, toIndex)
                : Collections.emptyList();
        int totalPages = safeSize > 0 ? (int) Math.ceil((double) totalElements / safeSize) : 1;

        PageResponseDTO<UserLeadStatusRowDTO> pageResponse = PageResponseDTO.<UserLeadStatusRowDTO>builder()
                .content(pagedContent)
                .page(safePage)
                .size(safeSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last((safePage + 1) >= totalPages)
                .build();

        return UserStatusAnalyticsResponseDTO.builder()
                .statuses(statusColumns)
                .users(pageResponse)
                .grandTotalStatusCounts(grandTotalStatusCounts)
                .grandTotal(grandTotal)
                .build();
    }

    public CourseUserStatusAnalyticsResponseDTO fetchCourseUserWiseAnalytics(
            LeadStatusAnalyticsFilterRequest filter, UserDataScope dataScope) {

        CourseStatusAnalyticsResponseDTO courseRes = fetchCourseWiseAnalytics(filter, dataScope);
        UserStatusAnalyticsResponseDTO userRes = fetchUserWiseAnalytics(filter, dataScope);

        Map<String, Object> grandTotals = new LinkedHashMap<>();
        Map<String, Object> courseGt = new LinkedHashMap<>();
        courseGt.put("statusCounts", courseRes.getGrandTotalStatusCounts());
        courseGt.put("total", courseRes.getGrandTotal());

        Map<String, Object> userGt = new LinkedHashMap<>();
        userGt.put("statusCounts", userRes.getGrandTotalStatusCounts());
        userGt.put("total", userRes.getGrandTotal());

        grandTotals.put("courseWise", courseGt);
        grandTotals.put("userWise", userGt);

        return CourseUserStatusAnalyticsResponseDTO.builder()
                .statuses(courseRes.getStatuses())
                .courses(courseRes.getCourses())
                .users(userRes.getUsers())
                .grandTotals(grandTotals)
                .build();
    }

    private void buildNativeScopeClauseAndParams(UserDataScope dataScope, StringBuilder scopeClause, Map<String, Object> scopeParams) {
        if (dataScope == null || dataScope.getScopeType() == ScopeType.SYSTEM) {
            scopeClause.append("1=1");
        } else if (dataScope.getScopeType() == ScopeType.SELF) {
            scopeClause.append("(l.assigned_to_id IS NOT NULL AND l.assigned_to_id = :scopeUserId)");
            scopeParams.put("scopeUserId", dataScope.getUserId());
        } else if (dataScope.getScopeType() == ScopeType.DEPARTMENT) {
            Set<UUID> deptIds = dataScope.getDepartmentIds();
            Set<UUID> deptUserIds = dataScope.getDepartmentUserIds();
            UUID userId = dataScope.getUserId();

            if (deptIds != null && !deptIds.isEmpty() && deptUserIds != null && !deptUserIds.isEmpty()) {
                scopeClause.append("((l.assigned_to_id IS NOT NULL AND l.assigned_to_id = :scopeUserId) OR (l.assigned_to_id IS NOT NULL AND l.department_id IN (:scopeDeptIds)) OR (l.assigned_to_id IS NOT NULL AND l.assigned_to_id IN (:scopeDeptUserIds)))");
                scopeParams.put("scopeUserId", userId);
                scopeParams.put("scopeDeptIds", deptIds);
                scopeParams.put("scopeDeptUserIds", deptUserIds);
            } else if (deptIds != null && !deptIds.isEmpty()) {
                scopeClause.append("((l.assigned_to_id IS NOT NULL AND l.assigned_to_id = :scopeUserId) OR (l.assigned_to_id IS NOT NULL AND l.department_id IN (:scopeDeptIds)))");
                scopeParams.put("scopeUserId", userId);
                scopeParams.put("scopeDeptIds", deptIds);
            } else {
                scopeClause.append("(l.assigned_to_id IS NOT NULL AND l.assigned_to_id = :scopeUserId)");
                scopeParams.put("scopeUserId", userId);
            }
        }
    }

    private void buildFilterClauseAndParams(LeadStatusAnalyticsFilterRequest filter, StringBuilder filterClause, Map<String, Object> params) {
        if (filter == null) return;

        if (filter.getLeadSourceId() != null) {
            filterClause.append(" AND EXISTS (SELECT 1 FROM lead_lead_sources lls WHERE lls.lead_id = l.id AND lls.lead_source_id = :leadSourceId) ");
            params.put("leadSourceId", filter.getLeadSourceId());
        }

        if (filter.getBoardId() != null) {
            filterClause.append(" AND l.board_id = :boardId ");
            params.put("boardId", filter.getBoardId());
        }

        if (filter.getGradeId() != null) {
            filterClause.append(" AND l.grade_id = :gradeId ");
            params.put("gradeId", filter.getGradeId());
        }

        if (filter.getAssignedUserId() != null) {
            filterClause.append(" AND l.assigned_to_id = :assignedUserId ");
            params.put("assignedUserId", filter.getAssignedUserId());
        }

        if (Boolean.TRUE.equals(filter.getAllotted())) {
            filterClause.append(" AND l.assigned_to_id IS NOT NULL ");
        } else if (Boolean.FALSE.equals(filter.getAllotted()) || Boolean.TRUE.equals(filter.getUnallotted())) {
            filterClause.append(" AND l.assigned_to_id IS NULL ");
        }

        // Status filter
        if (filter.getStatusIds() != null && !filter.getStatusIds().isEmpty()) {
            filterClause.append(" AND l.lead_status_id IN (:filteredStatusIds) ");
            params.put("filteredStatusIds", filter.getStatusIds());
        } else if (filter.getStatusId() != null) {
            filterClause.append(" AND l.lead_status_id = :filteredStatusId ");
            params.put("filteredStatusId", filter.getStatusId());
        }

        // Date range
        LocalDate start = filter.getEffectiveStartDate();
        LocalDate end = filter.getEffectiveEndDate();
        if (start != null) {
            filterClause.append(" AND l.created_at >= :startDate ");
            params.put("startDate", start.atStartOfDay());
        }
        if (end != null) {
            filterClause.append(" AND l.created_at <= :endDate ");
            params.put("endDate", end.atTime(LocalTime.MAX));
        }

        // Historical status filter
        if (filter.getLeadStatusHistoryIds() != null && !filter.getLeadStatusHistoryIds().isEmpty()) {
            filterClause.append(" AND EXISTS (SELECT 1 FROM lead_status_histories lsh WHERE lsh.lead_id = l.id AND lsh.is_deleted = false AND lsh.new_status_id IN (:histStatusIds)) ");
            params.put("histStatusIds", filter.getLeadStatusHistoryIds());
        } else if (filter.getLeadStatusHistoryId() != null) {
            filterClause.append(" AND EXISTS (SELECT 1 FROM lead_status_histories lsh WHERE lsh.lead_id = l.id AND lsh.is_deleted = false AND lsh.new_status_id = :histStatusId) ");
            params.put("histStatusId", filter.getLeadStatusHistoryId());
        }
    }

    private void bindParams(Query query, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            try {
                query.setParameter(entry.getKey(), entry.getValue());
            } catch (IllegalArgumentException ignored) {
                // Ignore parameter if not present in the specific query template
            }
        }
    }

    private void sortCourseRows(List<CourseLeadStatusRowDTO> rows, String sortBy, String sortDir) {
        boolean isAsc = "ASC".equalsIgnoreCase(sortDir);
        Comparator<CourseLeadStatusRowDTO> comp;

        if ("courseName".equalsIgnoreCase(sortBy) || "name".equalsIgnoreCase(sortBy)) {
            comp = Comparator.comparing(CourseLeadStatusRowDTO::getCourseName, String.CASE_INSENSITIVE_ORDER);
        } else if ("courseCode".equalsIgnoreCase(sortBy) || "code".equalsIgnoreCase(sortBy)) {
            comp = Comparator.comparing(r -> r.getCourseCode() != null ? r.getCourseCode() : "", String.CASE_INSENSITIVE_ORDER);
        } else if ("total".equalsIgnoreCase(sortBy)) {
            comp = Comparator.comparing(CourseLeadStatusRowDTO::getTotal);
        } else {
            // Check if sorting by a status code (e.g. RAW, CONNECTED)
            final String statusKey = sortBy.toUpperCase();
            comp = Comparator.comparing(r -> r.getStatusCounts().getOrDefault(statusKey, 0L));
        }

        if (!isAsc) {
            comp = comp.reversed();
        }
        rows.sort(comp);
    }

    private void sortUserRows(List<UserLeadStatusRowDTO> rows, String sortBy, String sortDir) {
        boolean isAsc = "ASC".equalsIgnoreCase(sortDir);
        Comparator<UserLeadStatusRowDTO> comp;

        if ("userName".equalsIgnoreCase(sortBy) || "name".equalsIgnoreCase(sortBy)) {
            comp = Comparator.comparing(UserLeadStatusRowDTO::getUserName, String.CASE_INSENSITIVE_ORDER);
        } else if ("email".equalsIgnoreCase(sortBy)) {
            comp = Comparator.comparing(r -> r.getEmail() != null ? r.getEmail() : "", String.CASE_INSENSITIVE_ORDER);
        } else if ("total".equalsIgnoreCase(sortBy)) {
            comp = Comparator.comparing(UserLeadStatusRowDTO::getTotal);
        } else {
            // Check if sorting by a status code (e.g. RAW, CONNECTED)
            final String statusKey = sortBy.toUpperCase();
            comp = Comparator.comparing(r -> r.getStatusCounts().getOrDefault(statusKey, 0L));
        }

        if (!isAsc) {
            comp = comp.reversed();
        }
        rows.sort(comp);
    }

    private UUID parseUUID(Object obj) {
        if (obj == null) return null;
        if (obj instanceof UUID u) return u;
        if (obj instanceof byte[] b) {
            if (b.length == 16) {
                ByteBuffer bb = ByteBuffer.wrap(b);
                return new UUID(bb.getLong(), bb.getLong());
            }
        }
        try {
            return UUID.fromString(obj.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private long parseLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number num) return num.longValue();
        try {
            return Long.parseLong(obj.toString().trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
