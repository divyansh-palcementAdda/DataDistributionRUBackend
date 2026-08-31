package com.app.datadistribution.service.util;

import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.User;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Domain utility for synchronizing Lead departments with the assigned user's authoritative department.
 * <p>
 * Business Invariants:
 * 1. When a lead has an assigned user (assignedTo != null), the lead's department MUST be derived
 *    from the assigned user's active department(s).
 * 2. If the user has a single active department, that department is authoritative (any conflicting
 *    client-supplied department is ignored).
 * 3. If the user has multiple mapped departments and the request specifies one of them, that matching
 *    department is used; otherwise, the user's primary/first active department is used.
 * 4. When a lead is unassigned (assignedTo == null), the requested department is preserved.
 */
public final class LeadDepartmentResolver {

    private LeadDepartmentResolver() {
        // Utility class
    }

    /**
     * Resolves the authoritative department for a lead based on the assigned user.
     *
     * @param assignedUser        The assigned user (can be null for unassigned leads).
     * @param requestedDepartment The department requested by the caller (or existing lead department).
     * @return The authoritative department for the lead.
     */
    public static Department resolveDepartmentForUser(User assignedUser, Department requestedDepartment) {
        if (assignedUser == null) {
            return requestedDepartment;
        }

        Set<Department> userDepartments = assignedUser.getDepartments();
        if (userDepartments == null || userDepartments.isEmpty()) {
            return requestedDepartment;
        }

        List<Department> activeDepartments = userDepartments.stream()
                .filter(d -> d != null && d.isActive() && !d.isDeleted())
                .collect(Collectors.toList());

        if (activeDepartments.isEmpty()) {
            return requestedDepartment;
        }

        // If a requested department is supplied and matches one of the user's active departments, honor it
        if (requestedDepartment != null && requestedDepartment.getId() != null) {
            for (Department dept : activeDepartments) {
                if (dept.getId() != null && dept.getId().equals(requestedDepartment.getId())) {
                    return dept;
                }
            }
        }

        // Otherwise, default to the user's primary/first active department
        return activeDepartments.get(0);
    }
}
