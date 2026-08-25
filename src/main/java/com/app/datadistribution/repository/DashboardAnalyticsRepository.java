package com.app.datadistribution.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest;
import com.app.datadistribution.dto.dashboard.DashboardAnalyticsResponseDTO;
import com.app.datadistribution.dto.dashboard.GroupCountDTO;
import com.app.datadistribution.entity.Board;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Grade;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.DashboardGroupBy;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DashboardAnalyticsRepository {

    private final EntityManager entityManager;
    private final IUserDataScopeService dataScopeService;

    public DashboardAnalyticsResponseDTO fetchAnalytics(User user, DashboardAnalyticsFilterRequest filter) throws UnauthorizedException, BadRequestException {
        if (filter == null) {
            filter = new DashboardAnalyticsFilterRequest();
        }
        UserDataScope dataScope = dataScopeService.getScopeForUser(user, filter);
        return fetchAnalyticsWithScope(dataScope, filter);
    }

    public DashboardAnalyticsResponseDTO fetchAnalytics(User user, Object legacyScope, DashboardAnalyticsFilterRequest filter) {
        try {
            return fetchAnalytics(user, filter);
        } catch (Exception e) {
            log.error("Error in fetchAnalytics for user {}", user.getUsername(), e);
            throw new RuntimeException(e);
        }
    }

    public DashboardAnalyticsResponseDTO fetchAnalyticsWithScope(UserDataScope dataScope, DashboardAnalyticsFilterRequest filter) {
        DashboardGroupBy groupBy = filter.getGroupBy() != null ? filter.getGroupBy() : DashboardGroupBy.LEAD_STATUS;

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // 1. Build Total Count Query for distinct matching leads
        long totalMatchingLeads = fetchTotalMatchingLeads(dataScope, filter);

        // 2. Build Group By Query
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Lead> root = query.from(Lead.class);

        List<Predicate> predicates = buildFilterPredicates(cb, root, dataScope, filter);
        query.where(predicates.toArray(new Predicate[0]));

        Path<UUID> idPath = null;
        Path<String> namePath = null;
        Path<String> codePath = null;
        Expression<String> concatNameExpr = null;

        switch (groupBy) {
            case LEAD_STATUS: {
                Join<Lead, LeadStatus> join = root.join("currentStatus", JoinType.INNER);
                predicates.add(cb.equal(join.get("isDeleted"), false));
                idPath = join.get("id");
                namePath = join.get("name");
                codePath = join.get("code");
                break;
            }
            case LEAD_SOURCE: {
                SetJoin<Lead, LeadSource> join = root.joinSet("leadSources", JoinType.INNER);
                predicates.add(cb.equal(join.get("isDeleted"), false));
                idPath = join.get("id");
                namePath = join.get("name");
                codePath = join.get("code");
                break;
            }
            case COURSE: {
                SetJoin<Lead, Course> join = root.joinSet("interestedCourses", JoinType.INNER);
                predicates.add(cb.equal(join.get("isDeleted"), false));
                idPath = join.get("id");
                namePath = join.get("courseName");
                codePath = join.get("courseCode");
                break;
            }
            case REGISTERED_COURSE: {
                Join<Lead, Course> join = root.join("course", JoinType.INNER);
                predicates.add(cb.equal(join.get("isDeleted"), false));
                idPath = join.get("id");
                namePath = join.get("courseName");
                codePath = join.get("courseCode");
                break;
            }
            case COURSE_TYPE: {
                SetJoin<Lead, Course> courseJoin = root.joinSet("interestedCourses", JoinType.INNER);
                Join<Course, CourseType> courseTypeJoin = courseJoin.join("courseType", JoinType.INNER);
                predicates.add(cb.equal(courseJoin.get("isDeleted"), false));
                predicates.add(cb.equal(courseTypeJoin.get("isDeleted"), false));
                idPath = courseTypeJoin.get("id");
                namePath = courseTypeJoin.get("name");
                break;
            }
            case BOARD: {
                Join<Lead, Board> join = root.join("board", JoinType.INNER);
                predicates.add(cb.equal(join.get("isDeleted"), false));
                idPath = join.get("id");
                namePath = join.get("name");
                codePath = join.get("code");
                break;
            }
            case GRADE: {
                Join<Lead, Grade> join = root.join("grade", JoinType.INNER);
                predicates.add(cb.equal(join.get("isDeleted"), false));
                idPath = join.get("id");
                namePath = join.get("name");
                codePath = join.get("code");
                break;
            }
            case DEPARTMENT: {
                Join<Lead, Department> join = root.join("department", JoinType.INNER);
                predicates.add(cb.equal(join.get("isDeleted"), false));
                idPath = join.get("id");
                namePath = join.get("name");
                codePath = join.get("code");
                break;
            }
            case ASSIGNED_USER: {
                Join<Lead, User> join = root.join("assignedTo", JoinType.INNER);
                predicates.add(cb.equal(join.get("isDeleted"), false));
                idPath = join.get("id");
                concatNameExpr = cb.concat(cb.concat(join.get("firstName"), " "), join.get("lastName"));
                codePath = join.get("username");
                break;
            }
        }

        query.where(predicates.toArray(new Predicate[0]));

        Expression<Long> countExpr = cb.countDistinct(root.get("id"));

        if (concatNameExpr != null) {
            query.select(cb.tuple(idPath.alias("id"), concatNameExpr.alias("name"), codePath.alias("code"), countExpr.alias("count")));
            query.groupBy(idPath, concatNameExpr, codePath);
        } else if (codePath != null) {
            query.select(cb.tuple(idPath.alias("id"), namePath.alias("name"), codePath.alias("code"), countExpr.alias("count")));
            query.groupBy(idPath, namePath, codePath);
        } else {
            query.select(cb.tuple(idPath.alias("id"), namePath.alias("name"), countExpr.alias("count")));
            query.groupBy(idPath, namePath);
        }

        // Sorting
        String sortBy = filter.getSortBy() != null ? filter.getSortBy().toUpperCase() : "COUNT";
        String sortDir = filter.getSortDirection() != null ? filter.getSortDirection().toUpperCase() : ("COUNT".equals(sortBy) ? "DESC" : "ASC");
        boolean isAsc = "ASC".equals(sortDir);

        if ("NAME".equals(sortBy)) {
            Expression<String> sortName = concatNameExpr != null ? concatNameExpr : namePath;
            query.orderBy(isAsc ? cb.asc(sortName) : cb.desc(sortName));
        } else if ("CODE".equals(sortBy) && codePath != null) {
            query.orderBy(isAsc ? cb.asc(codePath) : cb.desc(codePath));
        } else {
            query.orderBy(isAsc ? cb.asc(countExpr) : cb.desc(countExpr));
        }

        TypedQuery<Tuple> typedQuery = entityManager.createQuery(query);

        // Pagination
        Integer page = filter.getPage();
        Integer pageSize = filter.getEffectivePageSize();
        if (page != null && page >= 0 && pageSize != null && pageSize > 0) {
            typedQuery.setFirstResult(page * pageSize);
            typedQuery.setMaxResults(pageSize);
        }

        List<Tuple> tuples = typedQuery.getResultList();

        List<GroupCountDTO> groupData = new ArrayList<>();
        for (Tuple t : tuples) {
            UUID id = t.get("id", UUID.class);
            String name = t.get("name", String.class);
            String code = null;
            try {
                code = t.get("code", String.class);
            } catch (IllegalArgumentException ignored) {}

            long count = t.get("count", Long.class);
            double pct = totalMatchingLeads > 0 ? ((double) count / totalMatchingLeads) * 100.0 : 0.0;

            groupData.add(GroupCountDTO.builder()
                    .id(id)
                    .name(name)
                    .code(code)
                    .count(count)
                    .percentage(Math.round(pct * 100.0) / 100.0)
                    .build());
        }

        Integer totalPages = null;
        if (pageSize != null && pageSize > 0) {
            totalPages = (int) Math.ceil((double) totalMatchingLeads / pageSize);
        }

        return DashboardAnalyticsResponseDTO.builder()
                .groupBy(groupBy.name())
                .data(groupData)
                .total(totalMatchingLeads)
                .page(page)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .build();
    }

    public long fetchTotalMatchingLeads(UserDataScope dataScope, DashboardAnalyticsFilterRequest filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Lead> root = query.from(Lead.class);

        List<Predicate> predicates = buildFilterPredicates(cb, root, dataScope, filter);
        query.select(cb.countDistinct(root.get("id"))).where(predicates.toArray(new Predicate[0]));

        return entityManager.createQuery(query).getSingleResult();
    }

    public List<Predicate> buildFilterPredicates(CriteriaBuilder cb, Root<Lead> root, UserDataScope dataScope, DashboardAnalyticsFilterRequest filter) {
        List<Predicate> predicates = new ArrayList<>();

        // Base soft delete check
        predicates.add(cb.equal(root.get("isDeleted"), false));

        // Centralized Data Scope predicate
        if (dataScope.getScopeType() == ScopeType.SELF) {
            predicates.add(cb.and(
                    cb.isNotNull(root.get("assignedTo")),
                    cb.equal(root.get("assignedTo").get("id"), dataScope.getUserId())
            ));
        } else if (dataScope.getScopeType() == ScopeType.DEPARTMENT) {
            Predicate selfAssigned = cb.and(
                    cb.isNotNull(root.get("assignedTo")),
                    cb.equal(root.get("assignedTo").get("id"), dataScope.getUserId())
            );
            if (dataScope.getDepartmentIds() != null && !dataScope.getDepartmentIds().isEmpty()) {
                Predicate deptLeadPredicate = root.get("department").get("id").in(dataScope.getDepartmentIds());
                Predicate deptUserPredicate = (dataScope.getDepartmentUserIds() != null && !dataScope.getDepartmentUserIds().isEmpty())
                        ? root.get("assignedTo").get("id").in(dataScope.getDepartmentUserIds())
                        : cb.disjunction();
                predicates.add(cb.or(selfAssigned, deptLeadPredicate, deptUserPredicate));
            } else {
                predicates.add(selfAssigned);
            }
        }

        if (filter == null) {
            return predicates;
        }

        // Date Range
        LocalDate start = filter.getEffectiveStartDate();
        LocalDate end = filter.getEffectiveEndDate();
        if (start != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start.atStartOfDay()));
        }
        if (end != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end.atTime(LocalTime.MAX)));
        }

        // Department Filter (Intersect requested departmentIds with user data scope)
        if (filter.getDepartmentIds() != null && !filter.getDepartmentIds().isEmpty()) {
            Set<UUID> allowedDepts = new HashSet<>(filter.getDepartmentIds());
            if (!dataScope.isAdmin()) {
                allowedDepts.retainAll(dataScope.getDepartmentIds() != null ? dataScope.getDepartmentIds() : Collections.emptySet());
            }
            if (allowedDepts.isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get("department").get("id").in(allowedDepts));
            }
        }

        // Multi-value Status filter
        if (filter.getLeadStatusIds() != null && !filter.getLeadStatusIds().isEmpty()) {
            predicates.add(root.get("currentStatus").get("id").in(filter.getLeadStatusIds()));
        }

        // Multi-value Board filter
        if (filter.getBoardIds() != null && !filter.getBoardIds().isEmpty()) {
            predicates.add(root.get("board").get("id").in(filter.getBoardIds()));
        }

        // Multi-value Grade filter
        if (filter.getGradeIds() != null && !filter.getGradeIds().isEmpty()) {
            predicates.add(root.get("grade").get("id").in(filter.getGradeIds()));
        }

        // Multi-value Assigned User filter (Intersect with data scope)
        if (filter.getAssignedUserIds() != null && !filter.getAssignedUserIds().isEmpty()) {
            Set<UUID> allowedUsers = new HashSet<>(filter.getAssignedUserIds());
            if (dataScope.getScopeType() == ScopeType.SELF) {
                if (allowedUsers.contains(dataScope.getUserId())) {
                    predicates.add(cb.equal(root.get("assignedTo").get("id"), dataScope.getUserId()));
                } else {
                    predicates.add(cb.disjunction());
                }
            } else if (dataScope.getScopeType() == ScopeType.DEPARTMENT) {
                allowedUsers.retainAll(dataScope.getDepartmentUserIds() != null ? dataScope.getDepartmentUserIds() : Collections.emptySet());
                if (allowedUsers.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("assignedTo").get("id").in(allowedUsers));
                }
            } else {
                predicates.add(root.get("assignedTo").get("id").in(allowedUsers));
            }
        }

        // Multi-value Created By User filter
        if (filter.getCreatedByUserIds() != null && !filter.getCreatedByUserIds().isEmpty()) {
            predicates.add(root.get("createdByUser").get("id").in(filter.getCreatedByUserIds()));
        }

        // Multi-value Registered Course filter
        if (filter.getRegisteredCourseIds() != null && !filter.getRegisteredCourseIds().isEmpty()) {
            predicates.add(root.get("course").get("id").in(filter.getRegisteredCourseIds()));
        }

        // Multi-value Lead Source filter
        if (filter.getLeadSourceIds() != null && !filter.getLeadSourceIds().isEmpty()) {
            SetJoin<Lead, LeadSource> sourceJoin = root.joinSet("leadSources", JoinType.INNER);
            predicates.add(cb.equal(sourceJoin.get("isDeleted"), false));
            predicates.add(sourceJoin.get("id").in(filter.getLeadSourceIds()));
        }

        // Multi-value Interested Course filter
        if (filter.getCourseIds() != null && !filter.getCourseIds().isEmpty()) {
            SetJoin<Lead, Course> courseJoin = root.joinSet("interestedCourses", JoinType.INNER);
            predicates.add(cb.equal(courseJoin.get("isDeleted"), false));
            predicates.add(courseJoin.get("id").in(filter.getCourseIds()));
        }

        // Multi-value Course Type filter
        if (filter.getCourseTypeIds() != null && !filter.getCourseTypeIds().isEmpty()) {
            SetJoin<Lead, Course> interestedJoin = root.joinSet("interestedCourses", JoinType.LEFT);
            Join<Lead, Course> registeredJoin = root.join("course", JoinType.LEFT);
            predicates.add(cb.or(
                    interestedJoin.join("courseType", JoinType.LEFT).get("id").in(filter.getCourseTypeIds()),
                    registeredJoin.join("courseType", JoinType.LEFT).get("id").in(filter.getCourseTypeIds())
            ));
        }

        // Allotted / Unallotted filter
        if (Boolean.TRUE.equals(filter.getAllotted())) {
            predicates.add(cb.isNotNull(root.get("assignedTo")));
        } else if (Boolean.FALSE.equals(filter.getAllotted())) {
            predicates.add(cb.isNull(root.get("assignedTo")));
        }

        // Availed / Unavailed and Availed Date/User filters
        Boolean effectiveIsAvailed = filter.getEffectiveIsAvailed();
        boolean hasAvailedFilters = effectiveIsAvailed != null
                || filter.getAvailedByUserId() != null
                || (filter.getAvailedByUserIds() != null && !filter.getAvailedByUserIds().isEmpty())
                || filter.getAvailedFrom() != null
                || filter.getAvailedTo() != null;

        if (hasAvailedFilters) {
            jakarta.persistence.criteria.Subquery<UUID> subquery = entityManager.getCriteriaBuilder().createQuery().subquery(UUID.class);
            jakarta.persistence.criteria.Root<com.app.datadistribution.entity.LeadAvailed> availedRoot = subquery.from(com.app.datadistribution.entity.LeadAvailed.class);
            subquery.select(availedRoot.get("lead").get("id"));

            List<Predicate> subqueryPreds = new ArrayList<>();
            subqueryPreds.add(cb.equal(availedRoot.get("lead"), root));
            subqueryPreds.add(cb.equal(availedRoot.get("availedByUser"), root.get("assignedTo")));
            subqueryPreds.add(cb.equal(availedRoot.get("isDeleted"), false));

            if (filter.getAvailedByUserId() != null) {
                subqueryPreds.add(cb.equal(availedRoot.get("availedByUser").get("id"), filter.getAvailedByUserId()));
            }
            if (filter.getAvailedByUserIds() != null && !filter.getAvailedByUserIds().isEmpty()) {
                subqueryPreds.add(availedRoot.get("availedByUser").get("id").in(filter.getAvailedByUserIds()));
            }
            if (filter.getAvailedFrom() != null) {
                subqueryPreds.add(cb.greaterThanOrEqualTo(availedRoot.get("availedAt"), filter.getAvailedFrom().atStartOfDay()));
            }
            if (filter.getAvailedTo() != null) {
                subqueryPreds.add(cb.lessThanOrEqualTo(availedRoot.get("availedAt"), filter.getAvailedTo().atTime(LocalTime.MAX)));
            }
            subquery.where(subqueryPreds.toArray(new Predicate[0]));

            if (Boolean.FALSE.equals(effectiveIsAvailed)) {
                predicates.add(cb.not(cb.exists(subquery)));
            } else {
                predicates.add(cb.and(cb.isNotNull(root.get("assignedTo")), cb.exists(subquery)));
            }
        }

        // Assigned Date Range filter
        if (filter.getAssignedFrom() != null || filter.getAssignedTo() != null) {
            jakarta.persistence.criteria.Subquery<UUID> assignSubquery = entityManager.getCriteriaBuilder().createQuery().subquery(UUID.class);
            jakarta.persistence.criteria.Root<com.app.datadistribution.entity.LeadAssignmentHistory> assignRoot = assignSubquery.from(com.app.datadistribution.entity.LeadAssignmentHistory.class);
            assignSubquery.select(assignRoot.get("lead").get("id"));

            List<Predicate> assignPreds = new ArrayList<>();
            assignPreds.add(cb.equal(assignRoot.get("lead"), root));
            assignPreds.add(cb.equal(assignRoot.get("newAssignedUser"), root.get("assignedTo")));
            assignPreds.add(cb.equal(assignRoot.get("isDeleted"), false));
            if (filter.getAssignedFrom() != null) {
                assignPreds.add(cb.greaterThanOrEqualTo(assignRoot.get("createdAt"), filter.getAssignedFrom().atStartOfDay()));
            }
            if (filter.getAssignedTo() != null) {
                assignPreds.add(cb.lessThanOrEqualTo(assignRoot.get("createdAt"), filter.getAssignedTo().atTime(LocalTime.MAX)));
            }
            assignSubquery.where(assignPreds.toArray(new Predicate[0]));
            predicates.add(cb.and(cb.isNotNull(root.get("assignedTo")), cb.exists(assignSubquery)));
        }

        // Updated Date Range filter
        if (filter.getUpdatedFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), filter.getUpdatedFrom().atStartOfDay()));
        }
        if (filter.getUpdatedTo() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("updatedAt"), filter.getUpdatedTo().atTime(LocalTime.MAX)));
        }

        // Lead Code filter
        if (filter.getLeadCode() != null && !filter.getLeadCode().isBlank()) {
            predicates.add(cb.equal(cb.lower(root.get("leadCode")), filter.getLeadCode().trim().toLowerCase()));
        }

        // Generic Search Keyword filter
        if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
            String pattern = "%" + filter.getSearch().trim().toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("phoneNumber")), pattern),
                    cb.like(cb.lower(root.get("leadCode")), pattern),
                    cb.like(cb.lower(root.get("city")), pattern),
                    cb.like(cb.lower(root.get("state")), pattern),
                    cb.like(cb.lower(root.get("country")), pattern),
                    cb.like(cb.lower(root.get("courseInterested")), pattern)
            ));
        }

        return predicates;
    }
}
