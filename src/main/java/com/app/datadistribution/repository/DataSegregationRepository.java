package com.app.datadistribution.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.app.datadistribution.dto.segregation.BoardNodeDTO;
import com.app.datadistribution.dto.segregation.CourseTypeSegregationDTO;
import com.app.datadistribution.dto.segregation.GradeNodeDTO;
import com.app.datadistribution.dto.segregation.LeadStatusAnalyticsDTO;
import com.app.datadistribution.dto.segregation.LeadStatusColumnDTO;
import com.app.datadistribution.dto.segregation.SegregationMatrixResponseDTO;
import com.app.datadistribution.dto.segregation.SourceNodeDTO;
import com.app.datadistribution.dto.segregation.UserAnalyticsRowDTO;
import com.app.datadistribution.dto.segregation.UserSegregationAnalyticsDTO;
import com.app.datadistribution.entity.Board;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Grade;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAvailed;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DataSegregationRepository {

    private final EntityManager entityManager;
    private final CourseTypeRepository courseTypeRepository;
    private final LeadStatusRepository leadStatusRepository;

    /**
     * Fetch active course types with total matching leads for the current data scope.
     */
    public List<CourseTypeSegregationDTO> fetchCourseTypeSummary(UserDataScope dataScope) {
        List<CourseType> activeCourseTypes = courseTypeRepository.findAll().stream()
                .filter(ct -> !ct.isDeleted() && ct.getStatus() == Status.ACTIVE)
                .sorted(Comparator.comparing(CourseType::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        List<CourseTypeSegregationDTO> result = new ArrayList<>();

        for (CourseType ct : activeCourseTypes) {
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<Lead> root = query.from(Lead.class);

            List<Predicate> predicates = buildBaseScopePredicates(cb, root, dataScope);

            // Course Type filter: registered course OR interested courses
            SetJoin<Lead, Course> interestedJoin = root.joinSet("interestedCourses", JoinType.LEFT);
            Join<Lead, Course> registeredJoin = root.join("course", JoinType.LEFT);
            predicates.add(cb.or(
                    cb.equal(interestedJoin.join("courseType", JoinType.LEFT).get("id"), ct.getId()),
                    cb.equal(registeredJoin.join("courseType", JoinType.LEFT).get("id"), ct.getId())
            ));

            query.select(cb.countDistinct(root.get("id"))).where(predicates.toArray(new Predicate[0]));
            long count = entityManager.createQuery(query).getSingleResult();

            result.add(CourseTypeSegregationDTO.builder()
                    .id(ct.getId())
                    .name(ct.getName())
                    .description(ct.getDescription())
                    .totalLeads(count)
                    .build());
        }

        return result;
    }

    /**
     * Fetch hierarchical segregation matrix for the given Course Type, Lead Source, Board, and Grade.
     */
    public SegregationMatrixResponseDTO fetchSegregationMatrix(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId, UserDataScope dataScope) {
        CourseType courseType = courseTypeRepository.findById(courseTypeId).orElse(null);
        String courseTypeName = courseType != null ? courseType.getName() : "Unknown";

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // 1. Overall counts for the selected scope
        CriteriaQuery<Tuple> summaryQuery = cb.createTupleQuery();
        Root<Lead> summaryRoot = summaryQuery.from(Lead.class);
        List<Predicate> summaryPreds = buildScopeAndFilterPredicates(cb, summaryRoot, dataScope, courseTypeId, leadSourceId, boardId, gradeId);

        Subquery<UUID> availedSubquery = buildAvailedSubquery(cb, summaryQuery, summaryRoot);

        summaryQuery.multiselect(
                cb.countDistinct(summaryRoot.get("id")).alias("total"),
                cb.countDistinct(cb.selectCase().when(cb.isNotNull(summaryRoot.get("assignedTo")), summaryRoot.get("id")).otherwise(cb.nullLiteral(UUID.class))).alias("allotted"),
                cb.countDistinct(cb.selectCase().when(cb.isNull(summaryRoot.get("assignedTo")), summaryRoot.get("id")).otherwise(cb.nullLiteral(UUID.class))).alias("unallotted"),
                cb.countDistinct(cb.selectCase().when(cb.and(cb.isNotNull(summaryRoot.get("assignedTo")), cb.exists(availedSubquery)), summaryRoot.get("id")).otherwise(cb.nullLiteral(UUID.class))).alias("availed")
        ).where(summaryPreds.toArray(new Predicate[0]));

        Tuple summaryTuple = entityManager.createQuery(summaryQuery).getSingleResult();
        long totalOverall = summaryTuple.get("total", Long.class);
        long allottedOverall = summaryTuple.get("allotted", Long.class);
        long unallottedOverall = summaryTuple.get("unallotted", Long.class);
        long availedOverall = summaryTuple.get("availed", Long.class);

        // 2. Fetch grouped breakdown rows (Source -> Board -> Grade)
        CriteriaQuery<Tuple> matrixQuery = cb.createTupleQuery();
        Root<Lead> root = matrixQuery.from(Lead.class);

        List<Predicate> preds = buildScopeAndFilterPredicates(cb, root, dataScope, courseTypeId, leadSourceId, boardId, gradeId);

        SetJoin<Lead, LeadSource> sourceJoin = root.joinSet("leadSources", JoinType.INNER);
        preds.add(cb.equal(sourceJoin.get("isDeleted"), false));

        Join<Lead, Board> bJoin = root.join("board", JoinType.LEFT);
        Join<Lead, Grade> gJoin = root.join("grade", JoinType.LEFT);

        Subquery<UUID> matrixAvailedSubquery = buildAvailedSubquery(cb, matrixQuery, root);

        matrixQuery.multiselect(
                sourceJoin.get("id").alias("sourceId"),
                sourceJoin.get("name").alias("sourceName"),
                sourceJoin.get("code").alias("sourceCode"),
                bJoin.get("id").alias("boardId"),
                bJoin.get("name").alias("boardName"),
                bJoin.get("code").alias("boardCode"),
                gJoin.get("id").alias("gradeId"),
                gJoin.get("name").alias("gradeName"),
                gJoin.get("code").alias("gradeCode"),
                cb.countDistinct(root.get("id")).alias("total"),
                cb.countDistinct(cb.selectCase().when(cb.isNotNull(root.get("assignedTo")), root.get("id")).otherwise(cb.nullLiteral(UUID.class))).alias("allotted"),
                cb.countDistinct(cb.selectCase().when(cb.isNull(root.get("assignedTo")), root.get("id")).otherwise(cb.nullLiteral(UUID.class))).alias("unallotted"),
                cb.countDistinct(cb.selectCase().when(cb.and(cb.isNotNull(root.get("assignedTo")), cb.exists(matrixAvailedSubquery)), root.get("id")).otherwise(cb.nullLiteral(UUID.class))).alias("availed")
        ).where(preds.toArray(new Predicate[0]))
        .groupBy(
                sourceJoin.get("id"), sourceJoin.get("name"), sourceJoin.get("code"),
                bJoin.get("id"), bJoin.get("name"), bJoin.get("code"),
                gJoin.get("id"), gJoin.get("name"), gJoin.get("code")
        );

        List<Tuple> tuples = entityManager.createQuery(matrixQuery).getResultList();

        // 3. Assemble Hierarchical Response Tree
        Map<UUID, SourceNodeDTO> sourceMap = new LinkedHashMap<>();
        Map<UUID, Map<UUID, BoardNodeDTO>> boardMapBySource = new HashMap<>();

        for (Tuple t : tuples) {
            UUID sId = t.get("sourceId", UUID.class);
            String sName = t.get("sourceName", String.class);
            String sCode = t.get("sourceCode", String.class);

            UUID bId = t.get("boardId", UUID.class);
            String bName = t.get("boardName", String.class);
            String bCode = t.get("boardCode", String.class);

            UUID grId = t.get("gradeId", UUID.class);
            String grName = t.get("gradeName", String.class);
            String grCode = t.get("gradeCode", String.class);

            long countTotal = t.get("total", Long.class);
            long countAllotted = t.get("allotted", Long.class);
            long countUnallotted = t.get("unallotted", Long.class);
            long countAvailed = t.get("availed", Long.class);

            // Source Node
            SourceNodeDTO sourceNode = sourceMap.computeIfAbsent(sId, id -> SourceNodeDTO.builder()
                    .sourceId(id)
                    .sourceName(sName)
                    .sourceCode(sCode)
                    .total(0)
                    .allotted(0)
                    .unallotted(0)
                    .availed(0)
                    .boards(new ArrayList<>())
                    .build());

            sourceNode.setTotal(sourceNode.getTotal() + countTotal);
            sourceNode.setAllotted(sourceNode.getAllotted() + countAllotted);
            sourceNode.setUnallotted(sourceNode.getUnallotted() + countUnallotted);
            sourceNode.setAvailed(sourceNode.getAvailed() + countAvailed);

            // Board Node (if board present)
            if (bId != null || bName != null) {
                UUID safeBoardId = bId != null ? bId : UUID.fromString("00000000-0000-0000-0000-000000000000");
                Map<UUID, BoardNodeDTO> boardMap = boardMapBySource.computeIfAbsent(sId, k -> new LinkedHashMap<>());
                BoardNodeDTO boardNode = boardMap.computeIfAbsent(safeBoardId, id -> {
                    BoardNodeDTO node = BoardNodeDTO.builder()
                            .boardId(bId)
                            .boardName(bName != null ? bName : "Other Board")
                            .boardCode(bCode)
                            .total(0)
                            .allotted(0)
                            .unallotted(0)
                            .availed(0)
                            .grades(new ArrayList<>())
                            .build();
                    sourceNode.getBoards().add(node);
                    return node;
                });

                boardNode.setTotal(boardNode.getTotal() + countTotal);
                boardNode.setAllotted(boardNode.getAllotted() + countAllotted);
                boardNode.setUnallotted(boardNode.getUnallotted() + countUnallotted);
                boardNode.setAvailed(boardNode.getAvailed() + countAvailed);

                // Grade Node (if grade present)
                if (grId != null || grName != null) {
                    GradeNodeDTO gradeNode = GradeNodeDTO.builder()
                            .gradeId(grId)
                            .gradeName(grName != null ? grName : "Other Grade")
                            .gradeCode(grCode)
                            .total(countTotal)
                            .allotted(countAllotted)
                            .unallotted(countUnallotted)
                            .availed(countAvailed)
                            .build();
                    boardNode.getGrades().add(gradeNode);
                }
            }
        }

        List<SourceNodeDTO> sourceNodes = new ArrayList<>(sourceMap.values());
        sourceNodes.sort(Comparator.comparing(SourceNodeDTO::getSourceName, String.CASE_INSENSITIVE_ORDER));

        return SegregationMatrixResponseDTO.builder()
                .courseTypeId(courseTypeId)
                .courseTypeName(courseTypeName)
                .totalLeads(totalOverall)
                .allottedLeads(allottedOverall)
                .unallottedLeads(unallottedOverall)
                .availedLeads(availedOverall)
                .sources(sourceNodes)
                .build();
    }

    /**
     * Fetch user analytics breakdown for the given scope.
     */
    public UserSegregationAnalyticsDTO fetchUserAnalytics(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId, UserDataScope dataScope) {
        List<LeadStatus> activeStatuses = leadStatusRepository.findAll().stream()
                .filter(s -> !s.isDeleted() && s.isActive())
                .sorted(Comparator.comparing(LeadStatus::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        List<LeadStatusColumnDTO> statusColumns = activeStatuses.stream()
                .map(s -> LeadStatusColumnDTO.builder()
                        .statusId(s.getId())
                        .name(s.getName())
                        .code(s.getCode())
                        .sentimentCategory(s.getSentimentCategory() != null ? s.getSentimentCategory().name() : "NEUTRAL")
                        .displayOrder(s.getDisplayOrder())
                        .build())
                .collect(Collectors.toList());

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // Query per-user aggregates (Total, Allotted, Availed)
        CriteriaQuery<Tuple> userSummaryQuery = cb.createTupleQuery();
        Root<Lead> root = userSummaryQuery.from(Lead.class);
        List<Predicate> preds = buildScopeAndFilterPredicates(cb, root, dataScope, courseTypeId, leadSourceId, boardId, gradeId);
        preds.add(cb.isNotNull(root.get("assignedTo"))); // Only assigned leads for user analytics

        Join<Lead, User> userJoin = root.join("assignedTo", JoinType.INNER);
        preds.add(cb.equal(userJoin.get("isDeleted"), false));

        Subquery<UUID> availedSub = buildAvailedSubquery(cb, userSummaryQuery, root);

        userSummaryQuery.multiselect(
                userJoin.get("id").alias("userId"),
                userJoin.get("firstName").alias("firstName"),
                userJoin.get("lastName").alias("lastName"),
                userJoin.get("username").alias("username"),
                userJoin.get("email").alias("email"),
                cb.countDistinct(root.get("id")).alias("total"),
                cb.countDistinct(cb.selectCase().when(cb.exists(availedSub), root.get("id")).otherwise(cb.nullLiteral(UUID.class))).alias("availed")
        ).where(preds.toArray(new Predicate[0]))
        .groupBy(userJoin.get("id"), userJoin.get("firstName"), userJoin.get("lastName"), userJoin.get("username"), userJoin.get("email"));

        List<Tuple> userTuples = entityManager.createQuery(userSummaryQuery).getResultList();

        // Query per-user per-status breakdown
        CriteriaQuery<Tuple> statusBreakdownQuery = cb.createTupleQuery();
        Root<Lead> statusRoot = statusBreakdownQuery.from(Lead.class);
        List<Predicate> statusPreds = buildScopeAndFilterPredicates(cb, statusRoot, dataScope, courseTypeId, leadSourceId, boardId, gradeId);
        statusPreds.add(cb.isNotNull(statusRoot.get("assignedTo")));

        Join<Lead, User> statusUserJoin = statusRoot.join("assignedTo", JoinType.INNER);
        statusPreds.add(cb.equal(statusUserJoin.get("isDeleted"), false));

        Join<Lead, LeadStatus> leadStatusJoin = statusRoot.join("currentStatus", JoinType.INNER);
        statusPreds.add(cb.equal(leadStatusJoin.get("isDeleted"), false));

        statusBreakdownQuery.multiselect(
                statusUserJoin.get("id").alias("userId"),
                leadStatusJoin.get("id").alias("statusId"),
                leadStatusJoin.get("code").alias("statusCode"),
                cb.countDistinct(statusRoot.get("id")).alias("count")
        ).where(statusPreds.toArray(new Predicate[0]))
        .groupBy(statusUserJoin.get("id"), leadStatusJoin.get("id"), leadStatusJoin.get("code"));

        List<Tuple> statusTuples = entityManager.createQuery(statusBreakdownQuery).getResultList();

        Map<UUID, Map<String, Long>> statusCountByUser = new HashMap<>();
        for (Tuple st : statusTuples) {
            UUID uId = st.get("userId", UUID.class);
            String sCode = st.get("statusCode", String.class);
            UUID sId = st.get("statusId", UUID.class);
            long count = st.get("count", Long.class);

            Map<String, Long> map = statusCountByUser.computeIfAbsent(uId, k -> new HashMap<>());
            if (sCode != null) map.put(sCode, count);
            if (sId != null) map.put(sId.toString(), count);
        }

        List<UserAnalyticsRowDTO> userRows = new ArrayList<>();
        for (Tuple ut : userTuples) {
            UUID uId = ut.get("userId", UUID.class);
            String fName = ut.get("firstName", String.class);
            String lName = ut.get("lastName", String.class);
            String uName = ut.get("username", String.class);
            String email = ut.get("email", String.class);
            long total = ut.get("total", Long.class);
            long availed = ut.get("availed", Long.class);

            String fullName = (fName != null ? fName : "") + (lName != null ? " " + lName : "");
            fullName = fullName.trim();
            if (fullName.isEmpty()) fullName = uName;

            Map<String, Long> userStatusCounts = statusCountByUser.getOrDefault(uId, Collections.emptyMap());

            User userEntity = entityManager.find(User.class, uId);
            String departmentName = userEntity != null && userEntity.getDepartments() != null && !userEntity.getDepartments().isEmpty()
                    ? userEntity.getDepartments().stream().map(Department::getName).collect(Collectors.joining(", "))
                    : null;
            List<String> roleNames = userEntity != null && userEntity.getRoles() != null
                    ? userEntity.getRoles().stream().map(Role::getName).collect(Collectors.toList())
                    : Collections.emptyList();

            userRows.add(UserAnalyticsRowDTO.builder()
                    .userId(uId)
                    .fullName(fullName)
                    .username(uName)
                    .email(email)
                    .department(departmentName)
                    .roles(roleNames)
                    .total(total)
                    .allotted(total)
                    .unallotted(0)
                    .availed(availed)
                    .statusCounts(userStatusCounts)
                    .build());
        }

        userRows.sort(Comparator.comparing(UserAnalyticsRowDTO::getTotal, Comparator.reverseOrder()));

        return UserSegregationAnalyticsDTO.builder()
                .courseTypeId(courseTypeId)
                .leadSourceId(leadSourceId)
                .boardId(boardId)
                .gradeId(gradeId)
                .statusColumns(statusColumns)
                .users(userRows)
                .build();
    }

    /**
     * Fetch lead status analytics for the given scope.
     */
    public List<LeadStatusAnalyticsDTO> fetchLeadStatusAnalytics(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId, UserDataScope dataScope) {
        List<LeadStatus> activeStatuses = leadStatusRepository.findAll().stream()
                .filter(s -> !s.isDeleted() && s.isActive())
                .sorted(Comparator.comparing(LeadStatus::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Lead> root = query.from(Lead.class);

        List<Predicate> preds = buildScopeAndFilterPredicates(cb, root, dataScope, courseTypeId, leadSourceId, boardId, gradeId);
        Join<Lead, LeadStatus> statusJoin = root.join("currentStatus", JoinType.INNER);
        preds.add(cb.equal(statusJoin.get("isDeleted"), false));

        query.multiselect(
                statusJoin.get("id").alias("statusId"),
                cb.countDistinct(root.get("id")).alias("count")
        ).where(preds.toArray(new Predicate[0]))
        .groupBy(statusJoin.get("id"));

        List<Tuple> tuples = entityManager.createQuery(query).getResultList();
        Map<UUID, Long> countByStatusId = tuples.stream()
                .collect(Collectors.toMap(
                        t -> t.get("statusId", UUID.class),
                        t -> t.get("count", Long.class)
                ));

        return activeStatuses.stream()
                .map(s -> LeadStatusAnalyticsDTO.builder()
                        .statusId(s.getId())
                        .name(s.getName())
                        .code(s.getCode())
                        .sentimentCategory(s.getSentimentCategory() != null ? s.getSentimentCategory().name() : "NEUTRAL")
                        .displayOrder(s.getDisplayOrder())
                        .count(countByStatusId.getOrDefault(s.getId(), 0L))
                        .build())
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Predicate & Subquery Helpers
    // =========================================================================

    private List<Predicate> buildBaseScopePredicates(CriteriaBuilder cb, Root<Lead> root, UserDataScope dataScope) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("isDeleted"), false));

        if (dataScope == null || dataScope.getScopeType() == ScopeType.SYSTEM) {
            return predicates;
        }

        if (dataScope.getScopeType() == ScopeType.SELF) {
            predicates.add(cb.isNotNull(root.get("assignedTo")));
            predicates.add(cb.equal(root.get("assignedTo").get("id"), dataScope.getUserId()));
            return predicates;
        }

        if (dataScope.getScopeType() == ScopeType.DEPARTMENT) {
            Predicate selfAssigned = cb.and(
                    cb.isNotNull(root.get("assignedTo")),
                    cb.equal(root.get("assignedTo").get("id"), dataScope.getUserId())
            );
            if (dataScope.getDepartmentIds() != null && !dataScope.getDepartmentIds().isEmpty()) {
                Predicate deptLead = cb.and(
                        cb.isNotNull(root.get("assignedTo")),
                        root.get("department").get("id").in(dataScope.getDepartmentIds())
                );
                Predicate deptUser = (dataScope.getDepartmentUserIds() != null && !dataScope.getDepartmentUserIds().isEmpty())
                        ? root.get("assignedTo").get("id").in(dataScope.getDepartmentUserIds())
                        : cb.disjunction();
                predicates.add(cb.or(selfAssigned, deptLead, deptUser));
            } else {
                predicates.add(selfAssigned);
            }
        }

        return predicates;
    }

    private List<Predicate> buildScopeAndFilterPredicates(CriteriaBuilder cb, Root<Lead> root, UserDataScope dataScope,
                                                         UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) {
        List<Predicate> predicates = buildBaseScopePredicates(cb, root, dataScope);

        // Course Type filter
        if (courseTypeId != null) {
            SetJoin<Lead, Course> interestedJoin = root.joinSet("interestedCourses", JoinType.LEFT);
            Join<Lead, Course> registeredJoin = root.join("course", JoinType.LEFT);
            predicates.add(cb.or(
                    cb.equal(interestedJoin.join("courseType", JoinType.LEFT).get("id"), courseTypeId),
                    cb.equal(registeredJoin.join("courseType", JoinType.LEFT).get("id"), courseTypeId)
            ));
        }

        // Lead Source filter
        if (leadSourceId != null) {
            SetJoin<Lead, LeadSource> sourceJoin = root.joinSet("leadSources", JoinType.INNER);
            predicates.add(cb.equal(sourceJoin.get("isDeleted"), false));
            predicates.add(cb.equal(sourceJoin.get("id"), leadSourceId));
        }

        // Board filter
        if (boardId != null) {
            predicates.add(cb.equal(root.get("board").get("id"), boardId));
        }

        // Grade filter
        if (gradeId != null) {
            predicates.add(cb.equal(root.get("grade").get("id"), gradeId));
        }

        return predicates;
    }

    private Subquery<UUID> buildAvailedSubquery(CriteriaBuilder cb, CriteriaQuery<?> parentQuery, Root<Lead> leadRoot) {
        Subquery<UUID> subquery = parentQuery.subquery(UUID.class);
        Root<LeadAvailed> availedRoot = subquery.from(LeadAvailed.class);
        subquery.select(availedRoot.get("lead").get("id"));
        subquery.where(
                cb.equal(availedRoot.get("lead"), leadRoot),
                cb.equal(availedRoot.get("availedByUser"), leadRoot.get("assignedTo")),
                cb.equal(availedRoot.get("isDeleted"), false)
        );
        return subquery;
    }
}
