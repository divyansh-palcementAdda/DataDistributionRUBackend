package com.app.datadistribution.repository;

import java.nio.ByteBuffer;
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

import org.springframework.stereotype.Repository;

import com.app.datadistribution.common.PageResponseDTO;
import com.app.datadistribution.dto.segregation.BoardNodeDTO;
import com.app.datadistribution.dto.segregation.CourseSegregationResponseDTO;
import com.app.datadistribution.dto.segregation.CourseSegregationRowDTO;
import com.app.datadistribution.dto.segregation.CourseTypeSegregationDTO;
import com.app.datadistribution.dto.segregation.CourseUserSegregationResponseDTO;
import com.app.datadistribution.dto.segregation.CourseUserSegregationRowDTO;
import com.app.datadistribution.dto.segregation.DataSegregationCapabilitiesDTO;
import com.app.datadistribution.dto.segregation.GradeNodeDTO;
import com.app.datadistribution.dto.segregation.LeadStatusAnalyticsDTO;
import com.app.datadistribution.dto.segregation.LeadStatusColumnDTO;
import com.app.datadistribution.dto.segregation.SegregationMatrixResponseDTO;
import com.app.datadistribution.dto.segregation.SourceNodeDTO;
import com.app.datadistribution.dto.segregation.UserAnalyticsRowDTO;
import com.app.datadistribution.dto.segregation.UserSegregationAnalyticsDTO;
import jakarta.persistence.Query;
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
        DataSegregationCapabilitiesDTO defaultCaps = DataSegregationCapabilitiesDTO.builder()
                .canView(true)
                .canViewFullFlow(true)
                .canViewCourseType(true)
                .canViewSource(true)
                .canViewBoard(true)
                .canViewGrade(true)
                .canViewUserAnalytics(true)
                .canViewLeadStatusAnalytics(true)
                .build();
        return fetchSegregationMatrix(courseTypeId, leadSourceId, boardId, gradeId, dataScope, defaultCaps);
    }

    /**
     * Fetch hierarchical segregation matrix with explicit granular flow capability enforcement.
     */
    public SegregationMatrixResponseDTO fetchSegregationMatrix(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId,
                                                              UserDataScope dataScope, DataSegregationCapabilitiesDTO capabilities) {
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

        // If user cannot view source breakdown, return summary without child sources
        if (capabilities != null && !capabilities.isCanViewSource()) {
            return SegregationMatrixResponseDTO.builder()
                    .courseTypeId(courseTypeId)
                    .courseTypeName(courseTypeName)
                    .totalLeads(totalOverall)
                    .allottedLeads(allottedOverall)
                    .unallottedLeads(unallottedOverall)
                    .availedLeads(availedOverall)
                    .capabilities(capabilities)
                    .sources(new ArrayList<>())
                    .build();
        }

        boolean canViewBoard = capabilities == null || capabilities.isCanViewBoard();
        boolean canViewGrade = capabilities == null || capabilities.isCanViewGrade();

        // 2. Fetch grouped breakdown rows according to permitted depth
        CriteriaQuery<Tuple> matrixQuery = cb.createTupleQuery();
        Root<Lead> root = matrixQuery.from(Lead.class);

        List<Predicate> preds = buildScopeAndFilterPredicates(cb, root, dataScope, courseTypeId, leadSourceId, boardId, gradeId);

        SetJoin<Lead, LeadSource> sourceJoin = root.joinSet("leadSources", JoinType.INNER);
        preds.add(cb.equal(sourceJoin.get("isDeleted"), false));

        Subquery<UUID> matrixAvailedSubquery = buildAvailedSubquery(cb, matrixQuery, root);

        List<jakarta.persistence.criteria.Selection<?>> selections = new ArrayList<>();
        selections.add(sourceJoin.get("id").alias("sourceId"));
        selections.add(sourceJoin.get("name").alias("sourceName"));
        selections.add(sourceJoin.get("code").alias("sourceCode"));

        List<jakarta.persistence.criteria.Expression<?>> groupBys = new ArrayList<>();
        groupBys.add(sourceJoin.get("id"));
        groupBys.add(sourceJoin.get("name"));
        groupBys.add(sourceJoin.get("code"));

        Join<Lead, Board> bJoin = null;
        if (canViewBoard) {
            bJoin = root.join("board", JoinType.LEFT);
            selections.add(bJoin.get("id").alias("boardId"));
            selections.add(bJoin.get("name").alias("boardName"));
            selections.add(bJoin.get("code").alias("boardCode"));
            groupBys.add(bJoin.get("id"));
            groupBys.add(bJoin.get("name"));
            groupBys.add(bJoin.get("code"));
        }

        Join<Lead, Grade> gJoin = null;
        if (canViewBoard && canViewGrade) {
            gJoin = root.join("grade", JoinType.LEFT);
            selections.add(gJoin.get("id").alias("gradeId"));
            selections.add(gJoin.get("name").alias("gradeName"));
            selections.add(gJoin.get("code").alias("gradeCode"));
            groupBys.add(gJoin.get("id"));
            groupBys.add(gJoin.get("name"));
            groupBys.add(gJoin.get("code"));
        }

        selections.add(cb.countDistinct(root.get("id")).alias("total"));
        selections.add(cb.countDistinct(cb.selectCase().when(cb.isNotNull(root.get("assignedTo")), root.get("id")).otherwise(cb.nullLiteral(UUID.class))).alias("allotted"));
        selections.add(cb.countDistinct(cb.selectCase().when(cb.isNull(root.get("assignedTo")), root.get("id")).otherwise(cb.nullLiteral(UUID.class))).alias("unallotted"));
        selections.add(cb.countDistinct(cb.selectCase().when(cb.and(cb.isNotNull(root.get("assignedTo")), cb.exists(matrixAvailedSubquery)), root.get("id")).otherwise(cb.nullLiteral(UUID.class))).alias("availed"));

        matrixQuery.multiselect(selections).where(preds.toArray(new Predicate[0])).groupBy(groupBys);

        List<Tuple> tuples = entityManager.createQuery(matrixQuery).getResultList();

        // 3. Assemble Hierarchical Response Tree
        Map<UUID, SourceNodeDTO> sourceMap = new LinkedHashMap<>();
        Map<UUID, Map<UUID, BoardNodeDTO>> boardMapBySource = new HashMap<>();

        for (Tuple t : tuples) {
            UUID sId = t.get("sourceId", UUID.class);
            String sName = t.get("sourceName", String.class);
            String sCode = t.get("sourceCode", String.class);

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

            // Board Node (if board permitted and present)
            if (canViewBoard) {
                UUID bId = t.get("boardId", UUID.class);
                String bName = t.get("boardName", String.class);
                String bCode = t.get("boardCode", String.class);

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

                    // Grade Node (if grade permitted and present)
                    if (canViewGrade) {
                        UUID grId = t.get("gradeId", UUID.class);
                        String grName = t.get("gradeName", String.class);
                        String grCode = t.get("gradeCode", String.class);

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
                .capabilities(capabilities)
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

    // =========================================================================
    // Course-wise & User-wise Segregation
    // =========================================================================

    public CourseSegregationResponseDTO fetchCourseWiseSegregation(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId,
                                                                  String search, int page, int size,
                                                                  String sortBy, String sortDirection, UserDataScope dataScope) {
        CourseType courseType = courseTypeRepository.findById(courseTypeId).orElse(null);
        String courseTypeName = courseType != null ? courseType.getName() : "Unknown";

        String leadSourceName = null;
        if (leadSourceId != null) {
            LeadSource ls = entityManager.find(LeadSource.class, leadSourceId);
            leadSourceName = ls != null ? ls.getName() : null;
        }
        String boardName = null;
        if (boardId != null) {
            Board b = entityManager.find(Board.class, boardId);
            boardName = b != null ? b.getName() : null;
        }
        String gradeName = null;
        if (gradeId != null) {
            Grade g = entityManager.find(Grade.class, gradeId);
            gradeName = g != null ? g.getName() : null;
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // 1. Category overall summary
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

        // 2. Active lead status columns
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

        // 3. Build native scope clause and filter clause
        StringBuilder scopeClause = new StringBuilder();
        Map<String, Object> scopeParams = new HashMap<>();
        buildNativeScopeClauseAndParams(dataScope, scopeClause, scopeParams);

        StringBuilder filterClause = new StringBuilder();
        if (leadSourceId != null) {
            filterClause.append(" AND EXISTS (SELECT 1 FROM lead_lead_sources lls WHERE lls.lead_id = l.id AND lls.lead_source_id = :leadSourceId) ");
            scopeParams.put("leadSourceId", leadSourceId);
        }
        if (boardId != null) {
            filterClause.append(" AND l.board_id = :boardId ");
            scopeParams.put("boardId", boardId);
        }
        if (gradeId != null) {
            filterClause.append(" AND l.grade_id = :gradeId ");
            scopeParams.put("gradeId", gradeId);
        }

        // 4. Query active courses in course type
        StringBuilder courseSql = new StringBuilder();
        courseSql.append("SELECT c.id, c.course_name, c.course_code ")
                .append("FROM courses c ")
                .append("WHERE c.course_type_id = :courseTypeId AND c.is_deleted = false AND c.status = 'ACTIVE' ");
        if (search != null && !search.isBlank()) {
            courseSql.append("AND (LOWER(c.course_name) LIKE :searchPattern OR LOWER(c.course_code) LIKE :searchPattern) ");
        }
        courseSql.append("ORDER BY c.course_name ASC");

        Query courseQuery = entityManager.createNativeQuery(courseSql.toString());
        courseQuery.setParameter("courseTypeId", courseTypeId);
        if (search != null && !search.isBlank()) {
            courseQuery.setParameter("searchPattern", "%" + search.toLowerCase().trim() + "%");
        }
        List<?> courseRawRows = courseQuery.getResultList();

        // 5. Query Course Totals (Total, Allotted, Unallotted, Availed)
        String courseTotalsSql =
                "SELECT " +
                "  comb.course_id, " +
                "  COUNT(DISTINCT comb.lead_id) AS total, " +
                "  COUNT(DISTINCT CASE WHEN comb.assigned_to_id IS NOT NULL THEN comb.lead_id END) AS allotted, " +
                "  COUNT(DISTINCT CASE WHEN comb.assigned_to_id IS NULL THEN comb.lead_id END) AS unallotted, " +
                "  COUNT(DISTINCT CASE WHEN comb.is_availed = 1 THEN comb.lead_id END) AS availed " +
                "FROM ( " +
                "  SELECT DISTINCT " +
                "    l.id AS lead_id, " +
                "    c.id AS course_id, " +
                "    l.assigned_to_id AS assigned_to_id, " +
                "    CASE WHEN l.assigned_to_id IS NOT NULL AND EXISTS ( " +
                "      SELECT 1 FROM lead_availed la " +
                "      WHERE la.lead_id = l.id " +
                "        AND la.availed_by_user_id = l.assigned_to_id " +
                "        AND la.is_deleted = false " +
                "    ) THEN 1 ELSE 0 END AS is_availed " +
                "  FROM leads l " +
                "  JOIN courses c ON c.id = l.course_id AND c.is_deleted = false " +
                "  WHERE l.is_deleted = false AND c.course_type_id = :courseTypeId AND " + scopeClause + filterClause + " " +
                "  UNION " +
                "  SELECT DISTINCT " +
                "    l.id AS lead_id, " +
                "    c.id AS course_id, " +
                "    l.assigned_to_id AS assigned_to_id, " +
                "    CASE WHEN l.assigned_to_id IS NOT NULL AND EXISTS ( " +
                "      SELECT 1 FROM lead_availed la " +
                "      WHERE la.lead_id = l.id " +
                "        AND la.availed_by_user_id = l.assigned_to_id " +
                "        AND la.is_deleted = false " +
                "    ) THEN 1 ELSE 0 END AS is_availed " +
                "  FROM leads l " +
                "  JOIN lead_interested_courses lic ON lic.lead_id = l.id " +
                "  JOIN courses c ON c.id = lic.course_id AND c.is_deleted = false " +
                "  WHERE l.is_deleted = false AND c.course_type_id = :courseTypeId AND " + scopeClause + filterClause + " " +
                ") comb " +
                "GROUP BY comb.course_id";

        Query totalsQuery = entityManager.createNativeQuery(courseTotalsSql);
        totalsQuery.setParameter("courseTypeId", courseTypeId);
        for (Map.Entry<String, Object> entry : scopeParams.entrySet()) {
            totalsQuery.setParameter(entry.getKey(), entry.getValue());
        }
        List<?> rawTotals = totalsQuery.getResultList();

        Map<UUID, Object[]> totalsByCourse = new HashMap<>();
        for (Object item : rawTotals) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID cId = parseUUID(row[0]);
            if (cId != null) {
                totalsByCourse.put(cId, row);
            }
        }

        // 6. Query Course Status Breakdown
        String courseStatusSql =
                "SELECT " +
                "  comb.course_id, " +
                "  comb.lead_status_id, " +
                "  ls.code, " +
                "  COUNT(DISTINCT comb.lead_id) AS status_count " +
                "FROM ( " +
                "  SELECT DISTINCT " +
                "    l.id AS lead_id, " +
                "    c.id AS course_id, " +
                "    l.lead_status_id AS lead_status_id " +
                "  FROM leads l " +
                "  JOIN courses c ON c.id = l.course_id AND c.is_deleted = false " +
                "  WHERE l.is_deleted = false AND c.course_type_id = :courseTypeId AND " + scopeClause + filterClause + " " +
                "  UNION " +
                "  SELECT DISTINCT " +
                "    l.id AS lead_id, " +
                "    c.id AS course_id, " +
                "    l.lead_status_id AS lead_status_id " +
                "  FROM leads l " +
                "  JOIN lead_interested_courses lic ON lic.lead_id = l.id " +
                "  JOIN courses c ON c.id = lic.course_id AND c.is_deleted = false " +
                "  WHERE l.is_deleted = false AND c.course_type_id = :courseTypeId AND " + scopeClause + filterClause + " " +
                ") comb " +
                "JOIN lead_statuses ls ON ls.id = comb.lead_status_id AND ls.is_deleted = false " +
                "GROUP BY comb.course_id, comb.lead_status_id, ls.code";

        Query statusQuery = entityManager.createNativeQuery(courseStatusSql);
        statusQuery.setParameter("courseTypeId", courseTypeId);
        for (Map.Entry<String, Object> entry : scopeParams.entrySet()) {
            statusQuery.setParameter(entry.getKey(), entry.getValue());
        }
        List<?> rawStatuses = statusQuery.getResultList();

        Map<UUID, Map<String, Long>> statusCountsByCourse = new HashMap<>();
        for (Object item : rawStatuses) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID cId = parseUUID(row[0]);
            UUID sId = parseUUID(row[1]);
            String sCode = row.length > 2 && row[2] != null ? row[2].toString() : null;
            long count = row.length > 3 ? parseLong(row[3]) : 0L;

            if (cId != null) {
                Map<String, Long> m = statusCountsByCourse.computeIfAbsent(cId, k -> new HashMap<>());
                if (sCode != null) m.put(sCode, count);
                if (sId != null) m.put(sId.toString(), count);
            }
        }

        // 7. Assemble Course Rows
        List<CourseSegregationRowDTO> allCourseRows = new ArrayList<>();
        for (Object item : courseRawRows) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID cId = parseUUID(row[0]);
            String cName = row.length > 1 && row[1] != null ? row[1].toString() : "Unknown Course";
            String cCode = row.length > 2 && row[2] != null ? row[2].toString() : "";

            long total = 0L;
            long allotted = 0L;
            long unallotted = 0L;
            long availed = 0L;

            if (totalsByCourse.containsKey(cId)) {
                Object[] tRow = totalsByCourse.get(cId);
                total = parseLong(tRow[1]);
                allotted = parseLong(tRow[2]);
                unallotted = parseLong(tRow[3]);
                availed = parseLong(tRow[4]);
            }

            Map<String, Long> sCounts = statusCountsByCourse.getOrDefault(cId, Collections.emptyMap());

            allCourseRows.add(CourseSegregationRowDTO.builder()
                    .courseId(cId)
                    .courseName(cName)
                    .courseCode(cCode)
                    .total(total)
                    .allotted(allotted)
                    .unallotted(unallotted)
                    .availed(availed)
                    .statusCounts(sCounts)
                    .build());
        }

        // 8. Sort Course Rows
        Comparator<CourseSegregationRowDTO> comparator;
        String field = sortBy != null ? sortBy.toLowerCase().trim() : "total";
        switch (field) {
            case "coursename":
            case "name":
                comparator = Comparator.comparing(CourseSegregationRowDTO::getCourseName, String.CASE_INSENSITIVE_ORDER);
                break;
            case "coursecode":
            case "code":
                comparator = Comparator.comparing(CourseSegregationRowDTO::getCourseCode, String.CASE_INSENSITIVE_ORDER);
                break;
            case "allotted":
                comparator = Comparator.comparing(CourseSegregationRowDTO::getAllotted);
                break;
            case "unallotted":
            case "unallocated":
                comparator = Comparator.comparing(CourseSegregationRowDTO::getUnallotted);
                break;
            case "availed":
                comparator = Comparator.comparing(CourseSegregationRowDTO::getAvailed);
                break;
            case "total":
            default:
                comparator = Comparator.comparing(CourseSegregationRowDTO::getTotal);
                break;
        }

        boolean isDesc = sortDirection == null || "desc".equalsIgnoreCase(sortDirection.trim());
        if (isDesc) {
            comparator = comparator.reversed();
        }
        allCourseRows.sort(comparator);

        // 9. Paginate
        int totalElements = allCourseRows.size();
        int safeSize = size > 0 ? size : 10;
        int safePage = Math.max(page, 0);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<CourseSegregationRowDTO> pageContent = (fromIndex <= toIndex) ? allCourseRows.subList(fromIndex, toIndex) : Collections.emptyList();
        int totalPages = safeSize > 0 ? (int) Math.ceil((double) totalElements / safeSize) : 1;

        PageResponseDTO<CourseSegregationRowDTO> pageResponse = PageResponseDTO.<CourseSegregationRowDTO>builder()
                .content(pageContent)
                .page(safePage)
                .size(safeSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last((safePage + 1) >= totalPages)
                .build();

        return CourseSegregationResponseDTO.builder()
                .courseTypeId(courseTypeId)
                .courseTypeName(courseTypeName)
                .leadSourceId(leadSourceId)
                .leadSourceName(leadSourceName)
                .boardId(boardId)
                .boardName(boardName)
                .gradeId(gradeId)
                .gradeName(gradeName)
                .totalLeads(totalOverall)
                .allottedLeads(allottedOverall)
                .unallottedLeads(unallottedOverall)
                .availedLeads(availedOverall)
                .statusColumns(statusColumns)
                .courses(pageResponse)
                .build();
    }

    public CourseUserSegregationResponseDTO fetchCourseUserWiseSegregation(UUID courseId, UUID leadSourceId, UUID boardId, UUID gradeId,
                                                                          String search, int page, int size,
                                                                          String sortBy, String sortDirection, UserDataScope dataScope) {
        Course course = entityManager.find(Course.class, courseId);
        String courseName = course != null ? course.getCourseName() : "Unknown Course";
        String courseCode = course != null ? course.getCourseCode() : "";
        UUID courseTypeId = course != null && course.getCourseType() != null ? course.getCourseType().getId() : null;
        String courseTypeName = course != null && course.getCourseType() != null ? course.getCourseType().getName() : "";

        String leadSourceName = null;
        if (leadSourceId != null) {
            LeadSource ls = entityManager.find(LeadSource.class, leadSourceId);
            leadSourceName = ls != null ? ls.getName() : null;
        }
        String boardName = null;
        if (boardId != null) {
            Board b = entityManager.find(Board.class, boardId);
            boardName = b != null ? b.getName() : null;
        }
        String gradeName = null;
        if (gradeId != null) {
            Grade g = entityManager.find(Grade.class, gradeId);
            gradeName = g != null ? g.getName() : null;
        }

        // 1. Active lead status columns
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

        // 2. Build native scope clause and filter clause
        StringBuilder scopeClause = new StringBuilder();
        Map<String, Object> scopeParams = new HashMap<>();
        buildNativeScopeClauseAndParams(dataScope, scopeClause, scopeParams);

        StringBuilder filterClause = new StringBuilder();
        if (leadSourceId != null) {
            filterClause.append(" AND EXISTS (SELECT 1 FROM lead_lead_sources lls WHERE lls.lead_id = l.id AND lls.lead_source_id = :leadSourceId) ");
            scopeParams.put("leadSourceId", leadSourceId);
        }
        if (boardId != null) {
            filterClause.append(" AND l.board_id = :boardId ");
            scopeParams.put("boardId", boardId);
        }
        if (gradeId != null) {
            filterClause.append(" AND l.grade_id = :gradeId ");
            scopeParams.put("gradeId", gradeId);
        }

        // 3. Course overall summary
        String courseSummarySql =
                "SELECT " +
                "  COUNT(DISTINCT comb.lead_id) AS total, " +
                "  COUNT(DISTINCT CASE WHEN comb.assigned_to_id IS NOT NULL THEN comb.lead_id END) AS allotted, " +
                "  COUNT(DISTINCT CASE WHEN comb.assigned_to_id IS NULL THEN comb.lead_id END) AS unallotted, " +
                "  COUNT(DISTINCT CASE WHEN comb.is_availed = 1 THEN comb.lead_id END) AS availed " +
                "FROM ( " +
                "  SELECT DISTINCT " +
                "    l.id AS lead_id, " +
                "    l.assigned_to_id AS assigned_to_id, " +
                "    CASE WHEN l.assigned_to_id IS NOT NULL AND EXISTS ( " +
                "      SELECT 1 FROM lead_availed la " +
                "      WHERE la.lead_id = l.id " +
                "        AND la.availed_by_user_id = l.assigned_to_id " +
                "        AND la.is_deleted = false " +
                "    ) THEN 1 ELSE 0 END AS is_availed " +
                "  FROM leads l " +
                "  WHERE l.is_deleted = false AND l.course_id = :courseId AND " + scopeClause + filterClause + " " +
                "  UNION " +
                "  SELECT DISTINCT " +
                "    l.id AS lead_id, " +
                "    l.assigned_to_id AS assigned_to_id, " +
                "    CASE WHEN l.assigned_to_id IS NOT NULL AND EXISTS ( " +
                "      SELECT 1 FROM lead_availed la " +
                "      WHERE la.lead_id = l.id " +
                "        AND la.availed_by_user_id = l.assigned_to_id " +
                "        AND la.is_deleted = false " +
                "    ) THEN 1 ELSE 0 END AS is_availed " +
                "  FROM leads l " +
                "  JOIN lead_interested_courses lic ON lic.lead_id = l.id " +
                "  WHERE l.is_deleted = false AND lic.course_id = :courseId AND " + scopeClause + filterClause + " " +
                ") comb";

        Query summaryQuery = entityManager.createNativeQuery(courseSummarySql);
        summaryQuery.setParameter("courseId", courseId);
        for (Map.Entry<String, Object> entry : scopeParams.entrySet()) {
            summaryQuery.setParameter(entry.getKey(), entry.getValue());
        }
        Object summaryObj = summaryQuery.getSingleResult();
        Object[] sRow = summaryObj instanceof Object[] ? (Object[]) summaryObj : new Object[]{summaryObj};
        long totalLeads = sRow.length > 0 ? parseLong(sRow[0]) : 0L;
        long allottedLeads = sRow.length > 1 ? parseLong(sRow[1]) : 0L;
        long unallottedLeads = sRow.length > 2 ? parseLong(sRow[2]) : 0L;
        long availedLeads = sRow.length > 3 ? parseLong(sRow[3]) : 0L;

        // 4. Per-user (and unallocated) totals
        String userTotalsSql =
                "SELECT " +
                "  comb.assigned_to_id, " +
                "  u.first_name, " +
                "  u.last_name, " +
                "  u.username, " +
                "  u.email, " +
                "  COUNT(DISTINCT comb.lead_id) AS total, " +
                "  COUNT(DISTINCT CASE WHEN comb.assigned_to_id IS NOT NULL THEN comb.lead_id END) AS allotted, " +
                "  COUNT(DISTINCT CASE WHEN comb.assigned_to_id IS NULL THEN comb.lead_id END) AS unallotted, " +
                "  COUNT(DISTINCT CASE WHEN comb.is_availed = 1 THEN comb.lead_id END) AS availed " +
                "FROM ( " +
                "  SELECT DISTINCT " +
                "    l.id AS lead_id, " +
                "    l.assigned_to_id AS assigned_to_id, " +
                "    CASE WHEN l.assigned_to_id IS NOT NULL AND EXISTS ( " +
                "      SELECT 1 FROM lead_availed la " +
                "      WHERE la.lead_id = l.id " +
                "        AND la.availed_by_user_id = l.assigned_to_id " +
                "        AND la.is_deleted = false " +
                "    ) THEN 1 ELSE 0 END AS is_availed " +
                "  FROM leads l " +
                "  WHERE l.is_deleted = false AND l.course_id = :courseId AND " + scopeClause + filterClause + " " +
                "  UNION " +
                "  SELECT DISTINCT " +
                "    l.id AS lead_id, " +
                "    l.assigned_to_id AS assigned_to_id, " +
                "    CASE WHEN l.assigned_to_id IS NOT NULL AND EXISTS ( " +
                "      SELECT 1 FROM lead_availed la " +
                "      WHERE la.lead_id = l.id " +
                "        AND la.availed_by_user_id = l.assigned_to_id " +
                "        AND la.is_deleted = false " +
                "    ) THEN 1 ELSE 0 END AS is_availed " +
                "  FROM leads l " +
                "  JOIN lead_interested_courses lic ON lic.lead_id = l.id " +
                "  WHERE l.is_deleted = false AND lic.course_id = :courseId AND " + scopeClause + filterClause + " " +
                ") comb " +
                "LEFT JOIN users u ON u.id = comb.assigned_to_id AND u.is_deleted = false " +
                "GROUP BY comb.assigned_to_id, u.first_name, u.last_name, u.username, u.email";

        Query userTotalsQuery = entityManager.createNativeQuery(userTotalsSql);
        userTotalsQuery.setParameter("courseId", courseId);
        for (Map.Entry<String, Object> entry : scopeParams.entrySet()) {
            userTotalsQuery.setParameter(entry.getKey(), entry.getValue());
        }
        List<?> rawUserTotals = userTotalsQuery.getResultList();

        // 5. Per-user (and unallocated) status breakdown
        String userStatusSql =
                "SELECT " +
                "  comb.assigned_to_id, " +
                "  comb.lead_status_id, " +
                "  ls.code, " +
                "  COUNT(DISTINCT comb.lead_id) AS status_count " +
                "FROM ( " +
                "  SELECT DISTINCT " +
                "    l.id AS lead_id, " +
                "    l.assigned_to_id AS assigned_to_id, " +
                "    l.lead_status_id AS lead_status_id " +
                "  FROM leads l " +
                "  WHERE l.is_deleted = false AND l.course_id = :courseId AND " + scopeClause + filterClause + " " +
                "  UNION " +
                "  SELECT DISTINCT " +
                "    l.id AS lead_id, " +
                "    l.assigned_to_id AS assigned_to_id, " +
                "    l.lead_status_id AS lead_status_id " +
                "  FROM leads l " +
                "  JOIN lead_interested_courses lic ON lic.lead_id = l.id " +
                "  WHERE l.is_deleted = false AND lic.course_id = :courseId AND " + scopeClause + filterClause + " " +
                ") comb " +
                "JOIN lead_statuses ls ON ls.id = comb.lead_status_id AND ls.is_deleted = false " +
                "GROUP BY comb.assigned_to_id, comb.lead_status_id, ls.code";

        Query userStatusQuery = entityManager.createNativeQuery(userStatusSql);
        userStatusQuery.setParameter("courseId", courseId);
        for (Map.Entry<String, Object> entry : scopeParams.entrySet()) {
            userStatusQuery.setParameter(entry.getKey(), entry.getValue());
        }
        List<?> rawUserStatuses = userStatusQuery.getResultList();

        Map<UUID, Map<String, Long>> statusCountByUser = new HashMap<>();
        Map<String, Long> unallocatedStatusCounts = new HashMap<>();

        for (Object item : rawUserStatuses) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID uId = parseUUID(row[0]);
            UUID sId = parseUUID(row[1]);
            String sCode = row.length > 2 && row[2] != null ? row[2].toString() : null;
            long count = row.length > 3 ? parseLong(row[3]) : 0L;

            if (uId == null) {
                if (sCode != null) unallocatedStatusCounts.put(sCode, count);
                if (sId != null) unallocatedStatusCounts.put(sId.toString(), count);
            } else {
                Map<String, Long> m = statusCountByUser.computeIfAbsent(uId, k -> new HashMap<>());
                if (sCode != null) m.put(sCode, count);
                if (sId != null) m.put(sId.toString(), count);
            }
        }

        // 6. Build User Rows and Unallocated Row
        CourseUserSegregationRowDTO unallocatedRow = null;
        List<CourseUserSegregationRowDTO> userRows = new ArrayList<>();

        for (Object item : rawUserTotals) {
            Object[] row = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID uId = parseUUID(row[0]);
            long total = parseLong(row[5]);
            long allotted = parseLong(row[6]);
            long unallotted = parseLong(row[7]);
            long availed = parseLong(row[8]);

            if (uId == null) {
                unallocatedRow = CourseUserSegregationRowDTO.builder()
                        .userId(null)
                        .fullName("Unallocated")
                        .username(null)
                        .email(null)
                        .department(null)
                        .roles(Collections.emptyList())
                        .total(total)
                        .allotted(0)
                        .unallotted(total)
                        .availed(availed)
                        .statusCounts(unallocatedStatusCounts)
                        .unallocated(true)
                        .build();
            } else {
                String fName = row.length > 1 && row[1] != null ? row[1].toString() : "";
                String lName = row.length > 2 && row[2] != null ? row[2].toString() : "";
                String uName = row.length > 3 && row[3] != null ? row[3].toString() : "";
                String email = row.length > 4 && row[4] != null ? row[4].toString() : "";

                String fullName = (fName + " " + lName).trim();
                if (fullName.isEmpty()) fullName = uName;

                User userEntity = entityManager.find(User.class, uId);
                String departmentName = userEntity != null && userEntity.getDepartments() != null && !userEntity.getDepartments().isEmpty()
                        ? userEntity.getDepartments().stream().map(Department::getName).collect(Collectors.joining(", "))
                        : null;
                List<String> roleNames = userEntity != null && userEntity.getRoles() != null
                        ? userEntity.getRoles().stream().map(Role::getName).collect(Collectors.toList())
                        : Collections.emptyList();

                Map<String, Long> uStatusCounts = statusCountByUser.getOrDefault(uId, Collections.emptyMap());

                userRows.add(CourseUserSegregationRowDTO.builder()
                        .userId(uId)
                        .fullName(fullName)
                        .username(uName)
                        .email(email)
                        .department(departmentName)
                        .roles(roleNames)
                        .total(total)
                        .allotted(allotted)
                        .unallotted(unallotted)
                        .availed(availed)
                        .statusCounts(uStatusCounts)
                        .unallocated(false)
                        .build());
            }
        }

        // If unallottedLeads > 0 but unallocatedRow wasn't created (edge case), initialize empty unallocated row
        if (unallocatedRow == null && unallottedLeads > 0) {
            unallocatedRow = CourseUserSegregationRowDTO.builder()
                    .userId(null)
                    .fullName("Unallocated")
                    .total(unallottedLeads)
                    .allotted(0)
                    .unallotted(unallottedLeads)
                    .availed(0)
                    .statusCounts(unallocatedStatusCounts)
                    .unallocated(true)
                    .build();
        }

        // 7. Filter User Rows by Search
        if (search != null && !search.isBlank()) {
            String term = search.toLowerCase().trim();
            userRows = userRows.stream()
                    .filter(u -> (u.getFullName() != null && u.getFullName().toLowerCase().contains(term))
                            || (u.getUsername() != null && u.getUsername().toLowerCase().contains(term))
                            || (u.getEmail() != null && u.getEmail().toLowerCase().contains(term))
                            || (u.getDepartment() != null && u.getDepartment().toLowerCase().contains(term)))
                    .collect(Collectors.toList());
        }

        // 8. Sort User Rows
        Comparator<CourseUserSegregationRowDTO> comparator;
        String field = sortBy != null ? sortBy.toLowerCase().trim() : "total";
        switch (field) {
            case "name":
            case "fullname":
            case "username":
                comparator = Comparator.comparing(CourseUserSegregationRowDTO::getFullName, String.CASE_INSENSITIVE_ORDER);
                break;
            case "allotted":
                comparator = Comparator.comparing(CourseUserSegregationRowDTO::getAllotted);
                break;
            case "availed":
                comparator = Comparator.comparing(CourseUserSegregationRowDTO::getAvailed);
                break;
            case "total":
            default:
                comparator = Comparator.comparing(CourseUserSegregationRowDTO::getTotal);
                break;
        }

        boolean isDesc = sortDirection == null || "desc".equalsIgnoreCase(sortDirection.trim());
        if (isDesc) {
            comparator = comparator.reversed();
        }
        userRows.sort(comparator);

        // 9. Paginate User Rows
        int totalElements = userRows.size();
        int safeSize = size > 0 ? size : 10;
        int safePage = Math.max(page, 0);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<CourseUserSegregationRowDTO> pageContent = (fromIndex <= toIndex) ? userRows.subList(fromIndex, toIndex) : Collections.emptyList();
        int totalPages = safeSize > 0 ? (int) Math.ceil((double) totalElements / safeSize) : 1;

        PageResponseDTO<CourseUserSegregationRowDTO> pageResponse = PageResponseDTO.<CourseUserSegregationRowDTO>builder()
                .content(pageContent)
                .page(safePage)
                .size(safeSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last((safePage + 1) >= totalPages)
                .build();

        return CourseUserSegregationResponseDTO.builder()
                .courseId(courseId)
                .courseName(courseName)
                .courseCode(courseCode)
                .courseTypeId(courseTypeId)
                .courseTypeName(courseTypeName)
                .leadSourceId(leadSourceId)
                .leadSourceName(leadSourceName)
                .boardId(boardId)
                .boardName(boardName)
                .gradeId(gradeId)
                .gradeName(gradeName)
                .totalLeads(totalLeads)
                .allottedLeads(allottedLeads)
                .unallottedLeads(unallottedLeads)
                .availedLeads(availedLeads)
                .statusColumns(statusColumns)
                .unallocatedRow(unallocatedRow)
                .users(pageResponse)
                .build();
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

    private void buildNativeScopeClauseAndParams(UserDataScope dataScope, StringBuilder scopeClause, Map<String, Object> scopeParams) {
        boolean isSystemScope = dataScope == null || dataScope.getScopeType() == ScopeType.SYSTEM;
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
