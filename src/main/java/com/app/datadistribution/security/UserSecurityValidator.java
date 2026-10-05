package com.app.datadistribution.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.app.datadistribution.config.UserManagementProperties;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.enums.RoleType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSecurityValidator {

    public static final String ADMIN_CREATION_DISABLED_MESSAGE = "Admin and Super Admin user creation is currently disabled.";
    public static final String SUPER_ADMIN_RESTRICTED_MESSAGE = "Only SUPER_ADMIN can assign or create SUPER_ADMIN users.";
    public static final String ADMIN_RESTRICTED_MESSAGE = "You do not have permission to create or assign ADMIN users.";

    private final UserManagementProperties userManagementProperties;

    /**
     * Checks if a role is a privileged role (ADMIN or SUPER_ADMIN).
     */
    public boolean isPrivilegedRole(Role role) {
        return role != null && isPrivilegedRole(role.getName());
    }

    /**
     * Checks if a role name corresponds to a privileged role (ADMIN or SUPER_ADMIN).
     */
    public boolean isPrivilegedRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }
        String normalized = normalizeRoleName(roleName);
        return RoleType.ADMIN.name().equalsIgnoreCase(normalized)
                || RoleType.SUPER_ADMIN.name().equalsIgnoreCase(normalized);
    }

    /**
     * Checks if a role is SUPER_ADMIN.
     */
    public boolean isSuperAdminRole(Role role) {
        return role != null && isSuperAdminRole(role.getName());
    }

    /**
     * Checks if a role name is SUPER_ADMIN.
     */
    public boolean isSuperAdminRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }
        return RoleType.SUPER_ADMIN.name().equalsIgnoreCase(normalizeRoleName(roleName));
    }

    /**
     * Checks if a role is ADMIN.
     */
    public boolean isAdminRole(Role role) {
        return role != null && isAdminRole(role.getName());
    }

    /**
     * Checks if a role name is ADMIN.
     */
    public boolean isAdminRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }
        return RoleType.ADMIN.name().equalsIgnoreCase(normalizeRoleName(roleName));
    }

    /**
     * Checks if the currently authenticated user has SUPER_ADMIN role/authority.
     */
    public boolean isCurrentCallerSuperAdmin() {
        Set<String> authorities = getCurrentCallerAuthorities();
        return authorities.contains("ROLE_SUPER_ADMIN") || authorities.contains("SUPER_ADMIN");
    }

    /**
     * Checks if the currently authenticated user has ADMIN or SUPER_ADMIN role/authority.
     */
    public boolean isCurrentCallerAdminOrSuperAdmin() {
        Set<String> authorities = getCurrentCallerAuthorities();
        return authorities.contains("ROLE_SUPER_ADMIN")
                || authorities.contains("SUPER_ADMIN")
                || authorities.contains("ROLE_ADMIN")
                || authorities.contains("ADMIN");
    }

    /**
     * Validates whether requested roles can be assigned to a user during creation or update.
     * Prevents privileged role creation if allowAdminCreation is false.
     * Enforces RBAC to prevent privilege escalation even when allowAdminCreation is true.
     *
     * @param requestedRoleNames collection of role names being requested/assigned
     * @param existingRoleNames  existing role names of the user (empty if creating a new user)
     * @throws AccessDeniedException if validation fails
     */
    public void validateRoleAssignment(Collection<String> requestedRoleNames, Collection<String> existingRoleNames) {
        if (requestedRoleNames == null || requestedRoleNames.isEmpty()) {
            return;
        }

        Set<String> existingNormalized = existingRoleNames != null
                ? existingRoleNames.stream().map(this::normalizeRoleName).collect(Collectors.toSet())
                : Collections.emptySet();

        for (String requested : requestedRoleNames) {
            String normalizedRequested = normalizeRoleName(requested);

            if (isPrivilegedRole(normalizedRequested)) {
                // If user already had this privileged role, it is not a new privileged assignment (e.g. updating profile details)
                boolean alreadyHadRole = existingNormalized.contains(normalizedRequested);

                if (!alreadyHadRole) {
                    // 1. Feature Flag enforcement: Admin / Super Admin creation must be enabled
                    if (!userManagementProperties.isAllowAdminCreation()) {
                        log.warn("Blocked attempt to assign privileged role '{}' because allowAdminCreation is false", requested);
                        throw new AccessDeniedException(ADMIN_CREATION_DISABLED_MESSAGE);
                    }

                    // 2. Dynamic RBAC enforcement
                    if (isSuperAdminRole(normalizedRequested)) {
                        if (!isCurrentCallerSuperAdmin()) {
                            log.warn("Privilege escalation blocked: caller cannot assign SUPER_ADMIN role");
                            throw new AccessDeniedException(SUPER_ADMIN_RESTRICTED_MESSAGE);
                        }
                    } else if (isAdminRole(normalizedRequested)) {
                        if (!isCurrentCallerAdminOrSuperAdmin()) {
                            log.warn("Privilege escalation blocked: caller cannot assign ADMIN role");
                            throw new AccessDeniedException(ADMIN_RESTRICTED_MESSAGE);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks whether a role is eligible to be presented in user creation / role assignment options
     * based on the feature flag and current user's authorization.
     */
    public boolean isRoleAllowedForCreation(Role role) {
        if (role == null) {
            return false;
        }
        String roleName = role.getName();
        if (!isPrivilegedRole(roleName)) {
            // Normal operational roles (HOD, COUNSELOR, etc.) are always allowed
            return true;
        }

        // Privileged roles (ADMIN, SUPER_ADMIN) require feature flag to be enabled
        if (!userManagementProperties.isAllowAdminCreation()) {
            return false;
        }

        // When flag is enabled, RBAC governs visibility
        if (isSuperAdminRole(roleName)) {
            return isCurrentCallerSuperAdmin();
        }

        if (isAdminRole(roleName)) {
            return isCurrentCallerAdminOrSuperAdmin();
        }

        return false;
    }

    private Set<String> getCurrentCallerAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Collections.emptySet();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private String normalizeRoleName(String roleName) {
        if (roleName == null) {
            return "";
        }
        String trimmed = roleName.trim();
        if (trimmed.toUpperCase().startsWith("ROLE_")) {
            return trimmed.substring(5).toUpperCase();
        }
        return trimmed.toUpperCase();
    }
}
