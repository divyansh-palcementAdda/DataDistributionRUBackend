package com.app.datadistribution.repository;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import org.springframework.stereotype.Repository;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.common.PageResponseDTO;
import com.app.datadistribution.dto.course.CourseTypeResponseDTO;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CourseTypeRepositoryCustomImpl implements CourseTypeRepositoryCustom {

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public PageResponseDTO<CourseTypeResponseDTO> fetchCourseTypesWithLeadStats(PageRequestDTO pageRequest, UserDataScope dataScope) {
        int page = pageRequest.getPage() >= 0 ? pageRequest.getPage() : 0;
        int size = pageRequest.getSize() > 0 ? pageRequest.getSize() : 10;
        String search = pageRequest.getSearch() != null && !pageRequest.getSearch().trim().isEmpty()
                ? pageRequest.getSearch().trim()
                : null;

        // 1. Determine Scope Type
        boolean isSystemScope = dataScope == null || dataScope.getScopeType() == ScopeType.SYSTEM;

        // 2. Build Scope Clause and Parameters for Leads
        StringBuilder scopeClause = new StringBuilder();
        Map<String, Object> scopeParams = new HashMap<>();

        if (isSystemScope) {
            scopeClause.append("1=1");
        } else if (dataScope.getScopeType() == ScopeType.SELF) {
            scopeClause.append("l.assigned_to_id = :scopeUserId");
            scopeParams.put("scopeUserId", dataScope.getUserId());
        } else if (dataScope.getScopeType() == ScopeType.DEPARTMENT) {
            Set<UUID> deptIds = dataScope.getDepartmentIds();
            Set<UUID> deptUserIds = dataScope.getDepartmentUserIds();
            UUID userId = dataScope.getUserId();

            if (deptIds != null && !deptIds.isEmpty() && deptUserIds != null && !deptUserIds.isEmpty()) {
                scopeClause.append("(l.assigned_to_id = :scopeUserId OR l.department_id IN (:scopeDeptIds) OR l.assigned_to_id IN (:scopeDeptUserIds))");
                scopeParams.put("scopeUserId", userId);
                scopeParams.put("scopeDeptIds", deptIds);
                scopeParams.put("scopeDeptUserIds", deptUserIds);
            } else if (deptIds != null && !deptIds.isEmpty()) {
                scopeClause.append("(l.assigned_to_id = :scopeUserId OR l.department_id IN (:scopeDeptIds))");
                scopeParams.put("scopeUserId", userId);
                scopeParams.put("scopeDeptIds", deptIds);
            } else {
                scopeClause.append("l.assigned_to_id = :scopeUserId");
                scopeParams.put("scopeUserId", userId);
            }
        }

        // 3. Build Stats Subquery SQL
        String statsSubquerySql =
                "  SELECT " +
                "    lct.course_type_id, " +
                "    COUNT(DISTINCT lct.lead_id) AS total_data, " +
                "    COUNT(DISTINCT CASE WHEN lct.assigned_to_id IS NOT NULL THEN lct.lead_id END) AS total_allotted_data, " +
                "    COUNT(DISTINCT CASE WHEN lct.assigned_to_id IS NULL THEN lct.lead_id END) AS total_unallotted_data, " +
                "    COUNT(DISTINCT CASE WHEN lct.is_availed = 1 THEN lct.lead_id END) AS total_availed_data " +
                "  FROM (" +
                "    SELECT DISTINCT " +
                "      l.id AS lead_id, " +
                "      c.course_type_id AS course_type_id, " +
                "      l.assigned_to_id AS assigned_to_id, " +
                "      CASE WHEN l.assigned_to_id IS NOT NULL AND EXISTS (" +
                "        SELECT 1 FROM lead_availed la " +
                "        WHERE la.lead_id = l.id " +
                "          AND la.availed_by_user_id = l.assigned_to_id " +
                "          AND la.is_deleted = false" +
                "      ) THEN 1 ELSE 0 END AS is_availed " +
                "    FROM leads l " +
                "    JOIN courses c ON l.course_id = c.id AND c.is_deleted = false " +
                "    WHERE l.is_deleted = false AND " + scopeClause + " " +
                "    UNION " +
                "    SELECT DISTINCT " +
                "      l.id AS lead_id, " +
                "      c.course_type_id AS course_type_id, " +
                "      l.assigned_to_id AS assigned_to_id, " +
                "      CASE WHEN l.assigned_to_id IS NOT NULL AND EXISTS (" +
                "        SELECT 1 FROM lead_availed la " +
                "        WHERE la.lead_id = l.id " +
                "          AND la.availed_by_user_id = l.assigned_to_id " +
                "          AND la.is_deleted = false" +
                "      ) THEN 1 ELSE 0 END AS is_availed " +
                "    FROM leads l " +
                "    JOIN lead_interested_courses lic ON lic.lead_id = l.id " +
                "    JOIN courses c ON lic.course_id = c.id AND c.is_deleted = false " +
                "    WHERE l.is_deleted = false AND " + scopeClause + " " +
                "  ) lct " +
                "  GROUP BY lct.course_type_id ";

        // 4. Count Total Matching Course Types
        StringBuilder countSql = new StringBuilder();
        if (isSystemScope) {
            countSql.append("SELECT COUNT(ct.id) FROM course_types ct WHERE ct.is_deleted = false ");
        } else {
            countSql.append("SELECT COUNT(ct.id) FROM course_types ct ")
                    .append("INNER JOIN (").append(statsSubquerySql).append(") stats ON ct.id = stats.course_type_id ")
                    .append("WHERE ct.is_deleted = false AND stats.total_data > 0 ");
        }

        if (search != null) {
            countSql.append("AND (LOWER(ct.name) LIKE :searchPattern OR LOWER(ct.description) LIKE :searchPattern) ");
        }

        Query countQuery = entityManager.createNativeQuery(countSql.toString());
        if (!isSystemScope) {
            for (Map.Entry<String, Object> entry : scopeParams.entrySet()) {
                countQuery.setParameter(entry.getKey(), entry.getValue());
            }
        }
        if (search != null) {
            countQuery.setParameter("searchPattern", "%" + search.toLowerCase() + "%");
        }

        Number totalCountNum = (Number) countQuery.getSingleResult();
        long totalElements = totalCountNum != null ? totalCountNum.longValue() : 0L;
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        if (totalElements == 0) {
            return PageResponseDTO.<CourseTypeResponseDTO>builder()
                    .content(Collections.emptyList())
                    .page(page)
                    .size(size)
                    .totalElements(0L)
                    .totalPages(0)
                    .last(true)
                    .build();
        }

        // 5. Determine Server-Side Sorting Column
        String sortBy = pageRequest.getSortBy() != null ? pageRequest.getSortBy().trim() : "name";
        String sortDir = "DESC".equalsIgnoreCase(pageRequest.getSortDirection()) ? "DESC" : "ASC";

        String orderCol;
        switch (sortBy.toLowerCase()) {
            case "name":
                orderCol = "ct.name";
                break;
            case "description":
                orderCol = "ct.description";
                break;
            case "status":
                orderCol = "ct.status";
                break;
            case "createdat":
            case "created_at":
                orderCol = "ct.created_at";
                break;
            case "updatedat":
            case "updated_at":
                orderCol = "ct.updated_at";
                break;
            case "totaldata":
            case "total_data":
                orderCol = "COALESCE(stats.total_data, 0)";
                break;
            case "totalallotteddata":
            case "total_allotted_data":
                orderCol = "COALESCE(stats.total_allotted_data, 0)";
                break;
            case "totalunallotteddata":
            case "total_unallotted_data":
                orderCol = "COALESCE(stats.total_unallotted_data, 0)";
                break;
            case "totalavaileddata":
            case "total_availed_data":
                orderCol = "COALESCE(stats.total_availed_data, 0)";
                break;
            case "id":
                orderCol = "ct.id";
                break;
            default:
                orderCol = "ct.name";
                break;
        }

        String orderByClause = "ORDER BY " + orderCol + " " + sortDir + ", ct.name ASC, ct.id ASC";

        // 6. Build Main Data Query
        StringBuilder dataSql = new StringBuilder();
        dataSql.append("SELECT ")
                .append("ct.id AS id, ")
                .append("ct.name AS name, ")
                .append("ct.description AS description, ")
                .append("ct.status AS status, ")
                .append("ct.created_at AS created_at, ")
                .append("ct.updated_at AS updated_at, ")
                .append("COALESCE(stats.total_data, 0) AS total_data, ")
                .append("COALESCE(stats.total_allotted_data, 0) AS total_allotted_data, ")
                .append("COALESCE(stats.total_unallotted_data, 0) AS total_unallotted_data, ")
                .append("COALESCE(stats.total_availed_data, 0) AS total_availed_data ")
                .append("FROM course_types ct ");

        if (isSystemScope) {
            dataSql.append("LEFT JOIN (").append(statsSubquerySql).append(") stats ON ct.id = stats.course_type_id ")
                    .append("WHERE ct.is_deleted = false ");
        } else {
            dataSql.append("INNER JOIN (").append(statsSubquerySql).append(") stats ON ct.id = stats.course_type_id ")
                    .append("WHERE ct.is_deleted = false AND stats.total_data > 0 ");
        }

        if (search != null) {
            dataSql.append("AND (LOWER(ct.name) LIKE :searchPattern OR LOWER(ct.description) LIKE :searchPattern) ");
        }

        dataSql.append(orderByClause).append(" ");
        dataSql.append("LIMIT :limit OFFSET :offset");

        Query dataQuery = entityManager.createNativeQuery(dataSql.toString());
        for (Map.Entry<String, Object> entry : scopeParams.entrySet()) {
            dataQuery.setParameter(entry.getKey(), entry.getValue());
        }
        if (search != null) {
            dataQuery.setParameter("searchPattern", "%" + search.toLowerCase() + "%");
        }
        dataQuery.setParameter("limit", size);
        dataQuery.setParameter("offset", page * size);

        List<?> rawRows = dataQuery.getResultList();
        List<CourseTypeResponseDTO> content = new ArrayList<>(rawRows.size());

        for (Object item : rawRows) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID id = parseUUID(row[0]);
            String name = row.length > 1 && row[1] != null ? row[1].toString() : null;
            String description = row.length > 2 && row[2] != null ? row[2].toString() : null;
            Status status = row.length > 3 ? parseStatus(row[3]) : Status.ACTIVE;
            LocalDateTime createdAt = row.length > 4 ? parseLocalDateTime(row[4]) : null;
            LocalDateTime updatedAt = row.length > 5 ? parseLocalDateTime(row[5]) : null;
            long totalData = row.length > 6 ? parseLong(row[6]) : 0L;
            long totalAllottedData = row.length > 7 ? parseLong(row[7]) : 0L;
            long totalUnallottedData = row.length > 8 ? parseLong(row[8]) : 0L;
            long totalAvailedData = row.length > 9 ? parseLong(row[9]) : 0L;

            content.add(CourseTypeResponseDTO.builder()
                    .id(id)
                    .name(name)
                    .description(description)
                    .status(status)
                    .createdAt(createdAt)
                    .updatedAt(updatedAt)
                    .totalData(totalData)
                    .totalAllottedData(totalAllottedData)
                    .totalUnallottedData(totalUnallottedData)
                    .totalAvailedData(totalAvailedData)
                    .build());
        }

        return PageResponseDTO.<CourseTypeResponseDTO>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last((page + 1) >= totalPages)
                .build();
    }

    private UUID parseUUID(Object obj) {
        if (obj == null) return null;
        if (obj instanceof UUID) return (UUID) obj;
        if (obj instanceof byte[] b) {
            if (b.length == 16) {
                ByteBuffer bb = ByteBuffer.wrap(b);
                return new UUID(bb.getLong(), bb.getLong());
            }
        }
        try {
            return UUID.fromString(obj.toString());
        } catch (Exception e) {
            log.warn("Failed to parse UUID from object: {}", obj, e);
            return null;
        }
    }

    private Status parseStatus(Object obj) {
        if (obj == null) return Status.ACTIVE;
        if (obj instanceof Status) return (Status) obj;
        try {
            return Status.valueOf(obj.toString().toUpperCase());
        } catch (Exception e) {
            return Status.ACTIVE;
        }
    }

    private LocalDateTime parseLocalDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDateTime ldt) return ldt;
        if (obj instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (obj instanceof java.util.Date d) return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
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
}
