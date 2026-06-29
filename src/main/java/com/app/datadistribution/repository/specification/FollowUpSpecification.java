package com.app.datadistribution.repository.specification;

import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public class FollowUpSpecification {

    public static Specification<LeadFollowUp> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    public static Specification<LeadFollowUp> leadIsNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("lead").get("isDeleted"), false);
    }

    public static Specification<LeadFollowUp> belongsToUser(User user) {
        return (root, query, cb) -> cb.and(
            cb.equal(root.get("createdByUser"), user),
            cb.equal(root.get("lead").get("assignedTo"), user)
        );
    }

    public static Specification<LeadFollowUp> hasCreatedByUser(User user) {
        return (root, query, cb) -> cb.equal(root.get("createdByUser"), user);
    }

    public static Specification<LeadFollowUp> hasCreatedByUserId(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("createdByUser").get("id"), userId);
    }

    public static Specification<LeadFollowUp> hasLead(UUID leadId) {
        return (root, query, cb) -> cb.equal(root.get("lead").get("id"), leadId);
    }

    public static Specification<LeadFollowUp> hasStatus(FollowUpStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<LeadFollowUp> isCompleted(Boolean completed) {
        return (root, query, cb) -> cb.equal(root.get("completed"), completed);
    }

    public static Specification<LeadFollowUp> hasFollowUpDateOn(LocalDate date) {
        return (root, query, cb) -> {
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
            return cb.between(root.get("followUpDate"), startOfDay, endOfDay);
        };
    }

    public static Specification<LeadFollowUp> isOverdue() {
        return (root, query, cb) -> cb.and(
            cb.equal(root.get("completed"), false),
            cb.lessThan(root.get("followUpDate"), LocalDateTime.now())
        );
    }

    public static Specification<LeadFollowUp> search(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("remarks")), pattern),
                cb.like(cb.lower(root.get("lead").get("fullName")), pattern),
                cb.like(cb.lower(root.get("lead").get("leadCode")), pattern),
                cb.like(cb.lower(root.get("lead").get("email")), pattern),
                cb.like(cb.lower(root.get("lead").get("phoneNumber")), pattern)
            );
        };
    }
}
