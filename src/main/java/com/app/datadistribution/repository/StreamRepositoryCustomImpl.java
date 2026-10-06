package com.app.datadistribution.repository;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import org.springframework.stereotype.Repository;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.stream.StreamPageResponse;
import com.app.datadistribution.dto.stream.StreamResponse;
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
public class StreamRepositoryCustomImpl implements StreamRepositoryCustom {

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public StreamPageResponse fetchStreamsWithLeadStats(PageRequestDTO pageRequest, String status, UserDataScope dataScope) {
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
                "    sl.stream_id, " +
                "    COUNT(DISTINCT sl.lead_id) AS total_data, " +
                "    COUNT(DISTINCT CASE WHEN sl.assigned_to_id IS NOT NULL THEN sl.lead_id END) AS total_allotted_data, " +
                "    COUNT(DISTINCT CASE WHEN sl.assigned_to_id IS NULL THEN sl.lead_id END) AS total_unallotted_data, " +
                "    COUNT(DISTINCT CASE WHEN sl.is_availed = 1 THEN sl.lead_id END) AS total_availed_data " +
                "  FROM (" +
                "    SELECT DISTINCT " +
                "      l.id AS lead_id, " +
                "      l.stream_id AS stream_id, " +
                "      l.assigned_to_id AS assigned_to_id, " +
                "      CASE WHEN l.assigned_to_id IS NOT NULL AND EXISTS (" +
                "        SELECT 1 FROM lead_availed la " +
                "        WHERE la.lead_id = l.id " +
                "          AND la.availed_by_user_id = l.assigned_to_id " +
                "          AND la.is_deleted = false" +
                "      ) THEN 1 ELSE 0 END AS is_availed " +
                "    FROM leads l " +
                "    WHERE l.is_deleted = false AND l.stream_id IS NOT NULL AND " + scopeClause + " " +
                "  ) sl " +
                "  GROUP BY sl.stream_id ";

        // 4. Count Total Matching Streams
        StringBuilder countSql = new StringBuilder();
        if (isSystemScope) {
            countSql.append("SELECT COUNT(s.id) FROM lead_streams s WHERE s.is_deleted = false ");
        } else {
            countSql.append("SELECT COUNT(s.id) FROM lead_streams s ")
                    .append("INNER JOIN (").append(statsSubquerySql).append(") stats ON s.id = stats.stream_id ")
                    .append("WHERE s.is_deleted = false AND stats.total_data > 0 ");
        }

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            countSql.append("AND s.active = :activeStatus ");
        }

        if (search != null) {
            countSql.append("AND (LOWER(s.name) LIKE :searchPattern OR LOWER(s.code) LIKE :searchPattern OR LOWER(s.description) LIKE :searchPattern) ");
        }

        Query countQuery = entityManager.createNativeQuery(countSql.toString());
        if (!isSystemScope) {
            for (Map.Entry<String, Object> entry : scopeParams.entrySet()) {
                countQuery.setParameter(entry.getKey(), entry.getValue());
            }
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            boolean activeFilter = "ACTIVE".equalsIgnoreCase(status) || "true".equalsIgnoreCase(status);
            countQuery.setParameter("activeStatus", activeFilter);
        }
        if (search != null) {
            countQuery.setParameter("searchPattern", "%" + search.toLowerCase() + "%");
        }

        Number totalCountNum = (Number) countQuery.getSingleResult();
        long totalElements = totalCountNum != null ? totalCountNum.longValue() : 0L;
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        if (totalElements == 0) {
            return StreamPageResponse.builder()
                    .content(Collections.emptyList())
                    .pageNumber(page)
                    .pageSize(size)
                    .totalElements(0L)
                    .totalPages(0)
                    .last(true)
                    .build();
        }

        // 5. Determine Server-Side Sorting Column
        String sortBy = pageRequest.getSortBy() != null ? pageRequest.getSortBy().trim() : "displayOrder";
        String sortDir = "DESC".equalsIgnoreCase(pageRequest.getSortDirection()) ? "DESC" : "ASC";

        String orderCol;
        switch (sortBy.toLowerCase()) {
            case "name":
                orderCol = "s.name";
                break;
            case "code":
                orderCol = "s.code";
                break;
            case "description":
                orderCol = "s.description";
                break;
            case "active":
            case "status":
                orderCol = "s.active";
                break;
            case "displayorder":
            case "display_order":
                orderCol = "s.display_order";
                break;
            case "createdat":
            case "created_at":
                orderCol = "s.created_at";
                break;
            case "updatedat":
            case "updated_at":
                orderCol = "s.updated_at";
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
                orderCol = "s.id";
                break;
            default:
                orderCol = "s.display_order";
                break;
        }

        String orderByClause = "ORDER BY " + orderCol + " " + sortDir + ", s.name ASC, s.id ASC";

        // 6. Build Main Data Query
        StringBuilder dataSql = new StringBuilder();
        dataSql.append("SELECT ")
                .append("s.id AS id, ")
                .append("s.name AS name, ")
                .append("s.code AS code, ")
                .append("s.description AS description, ")
                .append("s.active AS active, ")
                .append("s.display_order AS display_order, ")
                .append("s.created_at AS created_at, ")
                .append("s.updated_at AS updated_at, ")
                .append("COALESCE(stats.total_data, 0) AS total_data, ")
                .append("COALESCE(stats.total_allotted_data, 0) AS total_allotted_data, ")
                .append("COALESCE(stats.total_unallotted_data, 0) AS total_unallotted_data, ")
                .append("COALESCE(stats.total_availed_data, 0) AS total_availed_data ")
                .append("FROM lead_streams s ");

        if (isSystemScope) {
            dataSql.append("LEFT JOIN (").append(statsSubquerySql).append(") stats ON s.id = stats.stream_id ")
                    .append("WHERE s.is_deleted = false ");
        } else {
            dataSql.append("INNER JOIN (").append(statsSubquerySql).append(") stats ON s.id = stats.stream_id ")
                    .append("WHERE s.is_deleted = false AND stats.total_data > 0 ");
        }

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            dataSql.append("AND s.active = :activeStatus ");
        }

        if (search != null) {
            dataSql.append("AND (LOWER(s.name) LIKE :searchPattern OR LOWER(s.code) LIKE :searchPattern OR LOWER(s.description) LIKE :searchPattern) ");
        }

        dataSql.append(orderByClause).append(" ");
        dataSql.append("LIMIT :limit OFFSET :offset");

        Query dataQuery = entityManager.createNativeQuery(dataSql.toString());
        for (Map.Entry<String, Object> entry : scopeParams.entrySet()) {
            dataQuery.setParameter(entry.getKey(), entry.getValue());
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            boolean activeFilter = "ACTIVE".equalsIgnoreCase(status) || "true".equalsIgnoreCase(status);
            dataQuery.setParameter("activeStatus", activeFilter);
        }
        if (search != null) {
            dataQuery.setParameter("searchPattern", "%" + search.toLowerCase() + "%");
        }
        dataQuery.setParameter("limit", size);
        dataQuery.setParameter("offset", page * size);

        List<?> rawRows = dataQuery.getResultList();
        List<StreamResponse> content = new ArrayList<>(rawRows.size());

        for (Object item : rawRows) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID id = parseUUID(row[0]);
            String name = row.length > 1 && row[1] != null ? row[1].toString() : null;
            String code = row.length > 2 && row[2] != null ? row[2].toString() : null;
            String description = row.length > 3 && row[3] != null ? row[3].toString() : null;
            boolean active = row.length > 4 && parseBoolean(row[4]);
            Integer displayOrder = row.length > 5 ? parseInteger(row[5]) : 0;
            LocalDateTime createdAt = row.length > 6 ? parseLocalDateTime(row[6]) : null;
            LocalDateTime updatedAt = row.length > 7 ? parseLocalDateTime(row[7]) : null;
            long totalData = row.length > 8 ? parseLong(row[8]) : 0L;
            long totalAllottedData = row.length > 9 ? parseLong(row[9]) : 0L;
            long totalUnallottedData = row.length > 10 ? parseLong(row[10]) : 0L;
            long totalAvailedData = row.length > 11 ? parseLong(row[11]) : 0L;

            content.add(StreamResponse.builder()
                    .id(id)
                    .name(name)
                    .code(code)
                    .description(description)
                    .active(active)
                    .status(active ? "ACTIVE" : "INACTIVE")
                    .displayOrder(displayOrder)
                    .createdAt(createdAt)
                    .updatedAt(updatedAt)
                    .totalData(totalData)
                    .totalAllottedData(totalAllottedData)
                    .totalUnallottedData(totalUnallottedData)
                    .totalAvailedData(totalAvailedData)
                    .build());
        }

        return StreamPageResponse.builder()
                .content(content)
                .pageNumber(page)
                .pageSize(size)
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
            return UUID.nameUUIDFromBytes(b);
        }
        if (obj instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private boolean parseBoolean(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof Number n) return n.intValue() != 0;
        String s = obj.toString().trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private Integer parseInteger(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString().trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private LocalDateTime parseLocalDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDateTime) return (LocalDateTime) obj;
        if (obj instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (obj instanceof java.util.Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return null;
    }
}
