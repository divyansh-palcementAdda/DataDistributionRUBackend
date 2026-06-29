package com.app.datadistribution.repository.specification;

import com.app.datadistribution.entity.LeadFeedback;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.LeadStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public class FeedbackSpecification {

    public static Specification<LeadFeedback> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    public static Specification<LeadFeedback> leadIsNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("lead").get("isDeleted"), false);
    }

    public static Specification<LeadFeedback> leadAssignedTo(User user) {
        return (root, query, cb) -> cb.equal(root.get("lead").get("assignedTo"), user);
    }

    public static Specification<LeadFeedback> hasCreatedByUser(User user) {
        return (root, query, cb) -> cb.equal(root.get("createdByUser"), user);
    }

    public static Specification<LeadFeedback> hasCreatedByUserId(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("createdByUser").get("id"), userId);
    }

    public static Specification<LeadFeedback> hasLead(UUID leadId) {
        return (root, query, cb) -> cb.equal(root.get("lead").get("id"), leadId);
    }

    public static Specification<LeadFeedback> createdToday() {
        return (root, query, cb) -> {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
            return cb.between(root.get("createdAt"), startOfDay, endOfDay);
        };
    }

    public static Specification<LeadFeedback> hasStatusAtTime(LeadStatus status) {
        return (root, query, cb) -> cb.equal(root.get("statusAtTime"), status);
    }

    public static Specification<LeadFeedback> hasStatusAtTimeIn(Collection<LeadStatus> statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.isEmpty()) {
                // If statuses is empty, return a predicate that is always false to avoid SQL syntax errors
                return cb.disjunction();
            }
            return root.get("statusAtTime").in(statuses);
        };
    }

    public static Specification<LeadFeedback> search(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("feedback")), pattern),
                cb.like(cb.lower(root.get("lead").get("fullName")), pattern),
                cb.like(cb.lower(root.get("lead").get("leadCode")), pattern),
                cb.like(cb.lower(root.get("lead").get("email")), pattern),
                cb.like(cb.lower(root.get("lead").get("phoneNumber")), pattern)
            );
        };
    }
}
