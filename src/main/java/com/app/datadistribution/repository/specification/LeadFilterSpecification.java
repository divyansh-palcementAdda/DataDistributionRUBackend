package com.app.datadistribution.repository.specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAvailed;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.LeadStatusHistory;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;

/**
 * Unified, deterministic JPA filter specification for Lead entity.
 * Uses correlated EXISTS subqueries for all collection associations (leadSources, interestedCourses,
 * availed, statusHistories) so the root query row count remains 1:1 with unique leads, preventing
 * row-duplication bugs in pagination and ensuring lead counts match Data Segregation numbers.
 */
public class LeadFilterSpecification {

    private LeadFilterSpecification() {}

    public static Specification<Lead> filterBySources(List<UUID> leadSourceIds) {
        return (root, query, cb) -> {
            if (leadSourceIds == null || leadSourceIds.isEmpty()) {
                return cb.conjunction();
            }
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<Lead> subLead = subquery.from(Lead.class);
            SetJoin<Lead, LeadSource> sourceJoin = subLead.joinSet("leadSources", JoinType.INNER);
            subquery.select(cb.literal(1));
            subquery.where(
                    cb.equal(subLead.get("id"), root.get("id")),
                    sourceJoin.get("id").in(leadSourceIds),
                    cb.isFalse(sourceJoin.get("isDeleted"))
            );
            return cb.exists(subquery);
        };
    }

    public static Specification<Lead> filterByMultiSource(Boolean multiSource) {
        return (root, query, cb) -> {
            if (multiSource == null) {
                return cb.conjunction();
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Lead> subLead = subquery.from(Lead.class);
            SetJoin<Lead, LeadSource> sourceJoin = subLead.joinSet("leadSources", JoinType.INNER);
            subquery.select(cb.countDistinct(sourceJoin.get("id")));
            subquery.where(
                    cb.equal(subLead.get("id"), root.get("id")),
                    cb.isFalse(sourceJoin.get("isDeleted"))
            );
            if (Boolean.TRUE.equals(multiSource)) {
                return cb.greaterThan(subquery, 1L);
            } else {
                return cb.lessThanOrEqualTo(subquery, 1L);
            }
        };
    }

    public static Specification<Lead> filterByCourse(UUID courseId) {
        return (root, query, cb) -> {
            if (courseId == null) {
                return cb.conjunction();
            }
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<Lead> subLead = subquery.from(Lead.class);
            Join<Lead, Course> regCourse = subLead.join("course", JoinType.LEFT);
            SetJoin<Lead, Course> intCourse = subLead.joinSet("interestedCourses", JoinType.LEFT);
            subquery.select(cb.literal(1));
            subquery.where(
                    cb.equal(subLead.get("id"), root.get("id")),
                    cb.or(
                            cb.and(cb.isNotNull(regCourse.get("id")), cb.equal(regCourse.get("id"), courseId), cb.isFalse(regCourse.get("isDeleted"))),
                            cb.and(cb.isNotNull(intCourse.get("id")), cb.equal(intCourse.get("id"), courseId), cb.isFalse(intCourse.get("isDeleted")))
                    )
            );
            return cb.exists(subquery);
        };
    }

    public static Specification<Lead> filterByCourseIds(List<UUID> courseIds) {
        return (root, query, cb) -> {
            if (courseIds == null || courseIds.isEmpty()) {
                return cb.conjunction();
            }
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<Lead> subLead = subquery.from(Lead.class);
            Join<Lead, Course> regCourse = subLead.join("course", JoinType.LEFT);
            SetJoin<Lead, Course> intCourse = subLead.joinSet("interestedCourses", JoinType.LEFT);
            subquery.select(cb.literal(1));
            subquery.where(
                    cb.equal(subLead.get("id"), root.get("id")),
                    cb.or(
                            cb.and(cb.isNotNull(regCourse.get("id")), regCourse.get("id").in(courseIds), cb.isFalse(regCourse.get("isDeleted"))),
                            cb.and(cb.isNotNull(intCourse.get("id")), intCourse.get("id").in(courseIds), cb.isFalse(intCourse.get("isDeleted")))
                    )
            );
            return cb.exists(subquery);
        };
    }

    public static Specification<Lead> filterByInterestedCourses(List<UUID> interestedCourseIds) {
        return (root, query, cb) -> {
            if (interestedCourseIds == null || interestedCourseIds.isEmpty()) {
                return cb.conjunction();
            }
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<Lead> subLead = subquery.from(Lead.class);
            SetJoin<Lead, Course> courseJoin = subLead.joinSet("interestedCourses", JoinType.INNER);
            subquery.select(cb.literal(1));
            subquery.where(
                    cb.equal(subLead.get("id"), root.get("id")),
                    courseJoin.get("id").in(interestedCourseIds),
                    cb.isFalse(courseJoin.get("isDeleted"))
            );
            return cb.exists(subquery);
        };
    }

    public static Specification<Lead> filterByCourseType(UUID courseTypeId) {
        return (root, query, cb) -> {
            if (courseTypeId == null) {
                return cb.conjunction();
            }
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<Lead> subLead = subquery.from(Lead.class);
            Join<Lead, Course> regCourse = subLead.join("course", JoinType.LEFT);
            Join<Course, com.app.datadistribution.entity.CourseType> regCt = regCourse.join("courseType", JoinType.LEFT);
            SetJoin<Lead, Course> intCourse = subLead.joinSet("interestedCourses", JoinType.LEFT);
            Join<Course, com.app.datadistribution.entity.CourseType> intCt = intCourse.join("courseType", JoinType.LEFT);
            subquery.select(cb.literal(1));
            subquery.where(
                    cb.equal(subLead.get("id"), root.get("id")),
                    cb.or(
                            cb.and(cb.isNotNull(regCourse.get("id")), cb.equal(regCt.get("id"), courseTypeId), cb.isFalse(regCourse.get("isDeleted"))),
                            cb.and(cb.isNotNull(intCourse.get("id")), cb.equal(intCt.get("id"), courseTypeId), cb.isFalse(intCourse.get("isDeleted")))
                    )
            );
            return cb.exists(subquery);
        };
    }

    public static Specification<Lead> filterByCourseTypeIds(List<UUID> courseTypeIds) {
        return (root, query, cb) -> {
            if (courseTypeIds == null || courseTypeIds.isEmpty()) {
                return cb.conjunction();
            }
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<Lead> subLead = subquery.from(Lead.class);
            Join<Lead, Course> regCourse = subLead.join("course", JoinType.LEFT);
            Join<Course, com.app.datadistribution.entity.CourseType> regCt = regCourse.join("courseType", JoinType.LEFT);
            SetJoin<Lead, Course> intCourse = subLead.joinSet("interestedCourses", JoinType.LEFT);
            Join<Course, com.app.datadistribution.entity.CourseType> intCt = intCourse.join("courseType", JoinType.LEFT);
            subquery.select(cb.literal(1));
            subquery.where(
                    cb.equal(subLead.get("id"), root.get("id")),
                    cb.or(
                            cb.and(cb.isNotNull(regCourse.get("id")), regCt.get("id").in(courseTypeIds), cb.isFalse(regCourse.get("isDeleted"))),
                            cb.and(cb.isNotNull(intCourse.get("id")), intCt.get("id").in(courseTypeIds), cb.isFalse(intCourse.get("isDeleted")))
                    )
            );
            return cb.exists(subquery);
        };
    }

    public static Specification<Lead> filterWithoutCourse() {
        return (root, query, cb) -> cb.isNull(root.get("course"));
    }

    public static Specification<Lead> filterByBoard(UUID boardId) {
        return (root, query, cb) -> boardId != null ? cb.equal(root.get("board").get("id"), boardId) : cb.conjunction();
    }

    public static Specification<Lead> filterByBoardIds(List<UUID> boardIds) {
        return (root, query, cb) -> (boardIds != null && !boardIds.isEmpty()) ? root.get("board").get("id").in(boardIds) : cb.conjunction();
    }

    public static Specification<Lead> filterByGrade(UUID gradeId) {
        return (root, query, cb) -> gradeId != null ? cb.equal(root.get("grade").get("id"), gradeId) : cb.conjunction();
    }

    public static Specification<Lead> filterByGradeIds(List<UUID> gradeIds) {
        return (root, query, cb) -> (gradeIds != null && !gradeIds.isEmpty()) ? root.get("grade").get("id").in(gradeIds) : cb.conjunction();
    }

    public static Specification<Lead> filterByDepartmentIds(List<UUID> departmentIds) {
        return (root, query, cb) -> (departmentIds != null && !departmentIds.isEmpty()) ? root.get("department").get("id").in(departmentIds) : cb.conjunction();
    }

    public static Specification<Lead> filterByAssignedUserIds(List<UUID> assignedUserIds) {
        return (root, query, cb) -> (assignedUserIds != null && !assignedUserIds.isEmpty()) ? root.get("assignedTo").get("id").in(assignedUserIds) : cb.conjunction();
    }

    public static Specification<Lead> filterByAllotted(Boolean allotted) {
        return (root, query, cb) -> {
            if (Boolean.TRUE.equals(allotted)) {
                return cb.isNotNull(root.get("assignedTo"));
            } else if (Boolean.FALSE.equals(allotted)) {
                return cb.isNull(root.get("assignedTo"));
            }
            return cb.conjunction();
        };
    }

    public static Specification<Lead> filterByStatus(UUID statusId) {
        return (root, query, cb) -> statusId != null ? cb.equal(root.get("currentStatus").get("id"), statusId) : cb.conjunction();
    }

    public static Specification<Lead> filterByStatusIds(List<UUID> statusIds) {
        return (root, query, cb) -> (statusIds != null && !statusIds.isEmpty()) ? root.get("currentStatus").get("id").in(statusIds) : cb.conjunction();
    }

    public static Specification<Lead> filterByCreatedDateRange(LocalDate startDate, LocalDate endDate) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (startDate != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate.atTime(LocalTime.MAX)));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }

    public static Specification<Lead> filterByUpdatedDateRange(LocalDate updatedFrom, LocalDate updatedTo) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (updatedFrom != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), updatedFrom.atStartOfDay()));
            }
            if (updatedTo != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("updatedAt"), updatedTo.atTime(LocalTime.MAX)));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }

    public static Specification<Lead> filterByAvailedDetails(Boolean isAvailed, UUID availedByUserId, List<UUID> availedByUserIds, LocalDate availedFrom, LocalDate availedTo) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<LeadAvailed> availedRoot = subquery.from(LeadAvailed.class);
            subquery.select(availedRoot.get("lead").get("id"));

            List<Predicate> subqueryPreds = new ArrayList<>();
            subqueryPreds.add(cb.equal(availedRoot.get("lead"), root));
            subqueryPreds.add(cb.equal(availedRoot.get("availedByUser"), root.get("assignedTo")));
            subqueryPreds.add(cb.equal(availedRoot.get("isDeleted"), false));

            if (availedByUserId != null) {
                subqueryPreds.add(cb.equal(availedRoot.get("availedByUser").get("id"), availedByUserId));
            }
            if (availedByUserIds != null && !availedByUserIds.isEmpty()) {
                subqueryPreds.add(availedRoot.get("availedByUser").get("id").in(availedByUserIds));
            }
            if (availedFrom != null) {
                subqueryPreds.add(cb.greaterThanOrEqualTo(availedRoot.get("availedAt"), availedFrom.atStartOfDay()));
            }
            if (availedTo != null) {
                subqueryPreds.add(cb.lessThanOrEqualTo(availedRoot.get("availedAt"), availedTo.atTime(LocalTime.MAX)));
            }
            subquery.where(subqueryPreds.toArray(new Predicate[0]));

            if (Boolean.FALSE.equals(isAvailed)) {
                return cb.not(cb.exists(subquery));
            } else {
                return cb.and(cb.isNotNull(root.get("assignedTo")), cb.exists(subquery));
            }
        };
    }

    public static Specification<Lead> filterByStatusHistory(List<UUID> statusIds) {
        return (root, query, cb) -> {
            if (statusIds == null || statusIds.isEmpty()) {
                return cb.conjunction();
            }
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<LeadStatusHistory> historyRoot = subquery.from(LeadStatusHistory.class);
            subquery.select(cb.literal(1));

            Predicate leadMatches = cb.equal(historyRoot.get("lead").get("id"), root.get("id"));
            Predicate notDeleted = cb.isFalse(historyRoot.get("isDeleted"));
            Predicate statusMatches = historyRoot.get("newStatus").get("id").in(statusIds);

            subquery.where(cb.and(leadMatches, notDeleted, statusMatches));
            return cb.exists(subquery);
        };
    }

    public static Specification<Lead> searchLeads(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String searchPattern = "%" + keyword.toLowerCase() + "%";

            // Correlated EXISTS subquery over interestedCourses.courseName — avoids row
            // duplication from the ManyToMany join while still enabling course-name search.
            Subquery<Integer> courseSearchSub = query.subquery(Integer.class);
            Root<Lead> courseSearchRoot = courseSearchSub.from(Lead.class);
            SetJoin<Lead, Course> courseSearchJoin = courseSearchRoot.joinSet("interestedCourses", JoinType.INNER);
            courseSearchSub.select(cb.literal(1));
            courseSearchSub.where(
                    cb.equal(courseSearchRoot.get("id"), root.get("id")),
                    cb.isFalse(courseSearchJoin.get("isDeleted")),
                    cb.like(cb.lower(courseSearchJoin.get("courseName")), searchPattern)
            );

            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), searchPattern),
                    cb.like(cb.lower(root.get("email")), searchPattern),
                    cb.like(cb.lower(root.get("phoneNumber")), searchPattern),
                    cb.like(cb.lower(root.get("leadCode")), searchPattern),
                    cb.like(cb.lower(root.get("city")), searchPattern),
                    cb.like(cb.lower(root.get("state")), searchPattern),
                    cb.like(cb.lower(root.get("country")), searchPattern),
                    cb.exists(courseSearchSub)
            );
        };
    }
}
