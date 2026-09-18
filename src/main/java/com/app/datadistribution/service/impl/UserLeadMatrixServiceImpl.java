package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.lead.CourseLeadMatrixRowDTO;
import com.app.datadistribution.dto.lead.LeadMatrixResponseDTO;
import com.app.datadistribution.dto.lead.LeadMatrixStatusDTO;
import com.app.datadistribution.dto.lead.ProgramLeadMatrixRowDTO;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourceNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import com.app.datadistribution.service.interfaces.IUserLeadMatrixService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserLeadMatrixServiceImpl implements IUserLeadMatrixService {

    private final UserRepository userRepository;
    private final LeadStatusRepository leadStatusRepository;
    private final IUserDataScopeService dataScopeService;

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public LeadMatrixResponseDTO<CourseLeadMatrixRowDTO> getCourseMatrix(UUID targetUserId) throws UnauthorizedException {
        validateAccess(targetUserId);

        List<LeadMatrixStatusDTO> statusDTOs = getActiveLeadStatuses();

        String totalsSql = """
            SELECT
                comb.course_id,
                comb.course_name,
                COUNT(DISTINCT comb.lead_id) AS total_allotted,
                COUNT(DISTINCT CASE WHEN EXISTS (
                    SELECT 1 FROM lead_availed la
                    WHERE la.lead_id = comb.lead_id
                      AND la.availed_by_user_id = comb.assigned_to_id
                      AND la.is_deleted = false
                ) THEN comb.lead_id END) AS total_availed
            FROM (
                SELECT DISTINCT
                    l.id AS lead_id,
                    c.id AS course_id,
                    c.course_name AS course_name,
                    l.assigned_to_id AS assigned_to_id
                FROM leads l
                JOIN courses c ON c.id = l.course_id AND c.is_deleted = false
                WHERE l.is_deleted = false AND l.assigned_to_id = :userId
                UNION
                SELECT DISTINCT
                    l.id AS lead_id,
                    c.id AS course_id,
                    c.course_name AS course_name,
                    l.assigned_to_id AS assigned_to_id
                FROM leads l
                JOIN lead_interested_courses lic ON lic.lead_id = l.id
                JOIN courses c ON c.id = lic.course_id AND c.is_deleted = false
                WHERE l.is_deleted = false AND l.assigned_to_id = :userId
            ) comb
            GROUP BY comb.course_id, comb.course_name
            ORDER BY comb.course_name ASC
            """;

        Query totalsQuery = entityManager.createNativeQuery(totalsSql);
        totalsQuery.setParameter("userId", targetUserId);
        List<?> totalsResult = totalsQuery.getResultList();

        Map<UUID, CourseLeadMatrixRowDTO> rowMap = new LinkedHashMap<>();
        for (Object item : totalsResult) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID courseId = parseUUID(row[0]);
            if (courseId == null) continue;

            String courseName = row[1] != null ? row[1].toString() : "";
            long totalAllotted = parseLong(row[2]);
            long totalAvailed = parseLong(row[3]);

            Map<String, Long> statusCounts = new LinkedHashMap<>();
            for (LeadMatrixStatusDTO st : statusDTOs) {
                statusCounts.put(st.getStatusId().toString(), 0L);
            }

            CourseLeadMatrixRowDTO rowDTO = CourseLeadMatrixRowDTO.builder()
                    .courseId(courseId)
                    .courseName(courseName)
                    .statusCounts(statusCounts)
                    .totalAllotted(totalAllotted)
                    .totalAvailed(totalAvailed)
                    .build();
            rowMap.put(courseId, rowDTO);
        }

        String countsSql = """
            SELECT
                comb.course_id,
                comb.course_name,
                comb.status_id,
                COUNT(DISTINCT comb.lead_id) AS cnt
            FROM (
                SELECT DISTINCT
                    l.id AS lead_id,
                    c.id AS course_id,
                    c.course_name AS course_name,
                    l.lead_status_id AS status_id
                FROM leads l
                JOIN courses c ON c.id = l.course_id AND c.is_deleted = false
                WHERE l.is_deleted = false AND l.assigned_to_id = :userId
                UNION
                SELECT DISTINCT
                    l.id AS lead_id,
                    c.id AS course_id,
                    c.course_name AS course_name,
                    l.lead_status_id AS status_id
                FROM leads l
                JOIN lead_interested_courses lic ON lic.lead_id = l.id
                JOIN courses c ON c.id = lic.course_id AND c.is_deleted = false
                WHERE l.is_deleted = false AND l.assigned_to_id = :userId
            ) comb
            WHERE comb.status_id IS NOT NULL
            GROUP BY comb.course_id, comb.course_name, comb.status_id
            ORDER BY comb.course_name ASC
            """;

        Query countsQuery = entityManager.createNativeQuery(countsSql);
        countsQuery.setParameter("userId", targetUserId);
        List<?> countsResult = countsQuery.getResultList();

        for (Object item : countsResult) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID courseId = parseUUID(row[0]);
            UUID statusId = parseUUID(row[2]);
            long count = parseLong(row[3]);

            if (courseId != null) {
                CourseLeadMatrixRowDTO dto = rowMap.computeIfAbsent(courseId, id -> {
                    Map<String, Long> initialCounts = new LinkedHashMap<>();
                    for (LeadMatrixStatusDTO st : statusDTOs) {
                        initialCounts.put(st.getStatusId().toString(), 0L);
                    }
                    return CourseLeadMatrixRowDTO.builder()
                            .courseId(id)
                            .courseName(row[1] != null ? row[1].toString() : "")
                            .statusCounts(initialCounts)
                            .totalAllotted(0L)
                            .totalAvailed(0L)
                            .build();
                });
                if (statusId != null) {
                    dto.getStatusCounts().put(statusId.toString(), count);
                }
            }
        }

        log.info("Course lead matrix for user {}: totalsResult={}, countsResult={}, rows={}",
                targetUserId, totalsResult.size(), countsResult.size(), rowMap.size());

        return LeadMatrixResponseDTO.<CourseLeadMatrixRowDTO>builder()
                .statuses(statusDTOs)
                .rows(new ArrayList<>(rowMap.values()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public LeadMatrixResponseDTO<ProgramLeadMatrixRowDTO> getProgramMatrix(UUID targetUserId) throws UnauthorizedException {
        validateAccess(targetUserId);

        List<LeadMatrixStatusDTO> statusDTOs = getActiveLeadStatuses();

        String totalsSql = """
            SELECT
                comb.program_id,
                comb.program_name,
                COUNT(DISTINCT comb.lead_id) AS total_allotted,
                COUNT(DISTINCT CASE WHEN EXISTS (
                    SELECT 1 FROM lead_availed la
                    WHERE la.lead_id = comb.lead_id
                      AND la.availed_by_user_id = comb.assigned_to_id
                      AND la.is_deleted = false
                ) THEN comb.lead_id END) AS total_availed
            FROM (
                SELECT DISTINCT
                    l.id AS lead_id,
                    p.id AS program_id,
                    p.name AS program_name,
                    l.assigned_to_id AS assigned_to_id
                FROM leads l
                JOIN programs p ON p.id = l.program_id AND p.is_deleted = false
                WHERE l.is_deleted = false AND l.assigned_to_id = :userId
                UNION
                SELECT DISTINCT
                    l.id AS lead_id,
                    p.id AS program_id,
                    p.name AS program_name,
                    l.assigned_to_id AS assigned_to_id
                FROM leads l
                JOIN courses c ON c.id = l.course_id AND c.is_deleted = false
                JOIN program_courses pc ON pc.course_id = c.id
                JOIN programs p ON p.id = pc.program_id AND p.is_deleted = false
                WHERE l.is_deleted = false AND l.assigned_to_id = :userId
                UNION
                SELECT DISTINCT
                    l.id AS lead_id,
                    p.id AS program_id,
                    p.name AS program_name,
                    l.assigned_to_id AS assigned_to_id
                FROM leads l
                JOIN lead_interested_courses lic ON lic.lead_id = l.id
                JOIN courses c ON c.id = lic.course_id AND c.is_deleted = false
                JOIN program_courses pc ON pc.course_id = c.id
                JOIN programs p ON p.id = pc.program_id AND p.is_deleted = false
                WHERE l.is_deleted = false AND l.assigned_to_id = :userId
            ) comb
            GROUP BY comb.program_id, comb.program_name
            ORDER BY comb.program_name ASC
            """;

        Query totalsQuery = entityManager.createNativeQuery(totalsSql);
        totalsQuery.setParameter("userId", targetUserId);
        List<?> totalsResult = totalsQuery.getResultList();

        Map<UUID, ProgramLeadMatrixRowDTO> rowMap = new LinkedHashMap<>();
        for (Object item : totalsResult) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID programId = parseUUID(row[0]);
            if (programId == null) continue;

            String programName = row[1] != null ? row[1].toString() : "";
            long totalAllotted = parseLong(row[2]);
            long totalAvailed = parseLong(row[3]);

            Map<String, Long> statusCounts = new LinkedHashMap<>();
            for (LeadMatrixStatusDTO st : statusDTOs) {
                statusCounts.put(st.getStatusId().toString(), 0L);
            }

            ProgramLeadMatrixRowDTO rowDTO = ProgramLeadMatrixRowDTO.builder()
                    .programId(programId)
                    .programName(programName)
                    .statusCounts(statusCounts)
                    .totalAllotted(totalAllotted)
                    .totalAvailed(totalAvailed)
                    .build();
            rowMap.put(programId, rowDTO);
        }

        String countsSql = """
            SELECT
                comb.program_id,
                comb.program_name,
                comb.status_id,
                COUNT(DISTINCT comb.lead_id) AS cnt
            FROM (
                SELECT DISTINCT
                    l.id AS lead_id,
                    p.id AS program_id,
                    p.name AS program_name,
                    l.lead_status_id AS status_id
                FROM leads l
                JOIN programs p ON p.id = l.program_id AND p.is_deleted = false
                WHERE l.is_deleted = false AND l.assigned_to_id = :userId
                UNION
                SELECT DISTINCT
                    l.id AS lead_id,
                    p.id AS program_id,
                    p.name AS program_name,
                    l.lead_status_id AS status_id
                FROM leads l
                JOIN courses c ON c.id = l.course_id AND c.is_deleted = false
                JOIN program_courses pc ON pc.course_id = c.id
                JOIN programs p ON p.id = pc.program_id AND p.is_deleted = false
                WHERE l.is_deleted = false AND l.assigned_to_id = :userId
                UNION
                SELECT DISTINCT
                    l.id AS lead_id,
                    p.id AS program_id,
                    p.name AS program_name,
                    l.lead_status_id AS status_id
                FROM leads l
                JOIN lead_interested_courses lic ON lic.lead_id = l.id
                JOIN courses c ON c.id = lic.course_id AND c.is_deleted = false
                JOIN program_courses pc ON pc.course_id = c.id
                JOIN programs p ON p.id = pc.program_id AND p.is_deleted = false
                WHERE l.is_deleted = false AND l.assigned_to_id = :userId
            ) comb
            WHERE comb.status_id IS NOT NULL
            GROUP BY comb.program_id, comb.program_name, comb.status_id
            ORDER BY comb.program_name ASC
            """;

        Query countsQuery = entityManager.createNativeQuery(countsSql);
        countsQuery.setParameter("userId", targetUserId);
        List<?> countsResult = countsQuery.getResultList();

        for (Object item : countsResult) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID programId = parseUUID(row[0]);
            UUID statusId = parseUUID(row[2]);
            long count = parseLong(row[3]);

            if (programId != null) {
                ProgramLeadMatrixRowDTO dto = rowMap.computeIfAbsent(programId, id -> {
                    Map<String, Long> initialCounts = new LinkedHashMap<>();
                    for (LeadMatrixStatusDTO st : statusDTOs) {
                        initialCounts.put(st.getStatusId().toString(), 0L);
                    }
                    return ProgramLeadMatrixRowDTO.builder()
                            .programId(id)
                            .programName(row[1] != null ? row[1].toString() : "")
                            .statusCounts(initialCounts)
                            .totalAllotted(0L)
                            .totalAvailed(0L)
                            .build();
                });
                if (statusId != null) {
                    dto.getStatusCounts().put(statusId.toString(), count);
                }
            }
        }

        log.info("Program lead matrix for user {}: totalsResult={}, countsResult={}, rows={}",
                targetUserId, totalsResult.size(), countsResult.size(), rowMap.size());

        return LeadMatrixResponseDTO.<ProgramLeadMatrixRowDTO>builder()
                .statuses(statusDTOs)
                .rows(new ArrayList<>(rowMap.values()))
                .build();
    }

    private List<LeadMatrixStatusDTO> getActiveLeadStatuses() {
        List<LeadStatus> statuses = leadStatusRepository.findByActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc();
        return statuses.stream()
                .map(s -> LeadMatrixStatusDTO.builder()
                        .statusId(s.getId())
                        .name(s.getName())
                        .code(s.getCode())
                        .sentimentCategory(s.getSentimentCategory() != null ? s.getSentimentCategory().name() : "NEUTRAL")
                        .followUpStatus(s.isFollowUpStatus())
                        .displayOrder(s.getDisplayOrder() != null ? s.getDisplayOrder() : 0)
                        .build())
                .collect(Collectors.toList());
    }

    private void validateAccess(UUID targetUserId) throws UnauthorizedException {
        UserDataScope scope;
        try {
            scope = dataScopeService.getScopeForCurrentUser();
        } catch (BadRequestException e) {
            throw new UnauthorizedException("User data scope could not be determined: " + e.getMessage());
        }
        if (scope == null) {
            throw new UnauthorizedException("User data scope could not be determined");
        }

        User targetUser = userRepository.findById(targetUserId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + targetUserId));

        if (scope.isSelfScope()) {
            if (!targetUserId.equals(scope.getUserId())) {
                throw new UnauthorizedException("Counselors can only view their own lead matrix.");
            }
        } else if (scope.isDepartmentScope()) {
            boolean isSelf = targetUserId.equals(scope.getUserId());
            boolean isDeptMember = scope.getDepartmentUserIds() != null && scope.getDepartmentUserIds().contains(targetUserId);
            if (!isSelf && !isDeptMember) {
                throw new UnauthorizedException("You can only view lead matrices for users in your department.");
            }
        }
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
            log.warn("Failed to parse UUID from object: {}", obj, e);
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
