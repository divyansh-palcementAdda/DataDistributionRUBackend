package com.app.datadistribution.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.config.UserManagementProperties;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.enums.RoleType;

class UserSecurityValidatorTest {

    private UserManagementProperties properties;
    private UserSecurityValidator validator;

    private Role superAdminRole;
    private Role adminRole;
    private Role hodRole;
    private Role counselorRole;

    @BeforeEach
    void setUp() {
        properties = new UserManagementProperties();
        validator = new UserSecurityValidator(properties);

        superAdminRole = Role.builder().name(RoleType.SUPER_ADMIN.name()).build();
        adminRole = Role.builder().name(RoleType.ADMIN.name()).build();
        hodRole = Role.builder().name(RoleType.HOD.name()).build();
        counselorRole = Role.builder().name(RoleType.COUNSELOR.name()).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String roleAuthority) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "testuser",
                "password",
                List.of(new SimpleGrantedAuthority(roleAuthority))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Role identification helpers should identify privileged and non-privileged roles accurately")
    void testRoleIdentification() {
        assertTrue(validator.isPrivilegedRole(adminRole));
        assertTrue(validator.isPrivilegedRole(superAdminRole));
        assertFalse(validator.isPrivilegedRole(hodRole));
        assertFalse(validator.isPrivilegedRole(counselorRole));

        assertTrue(validator.isPrivilegedRole("ADMIN"));
        assertTrue(validator.isPrivilegedRole("SUPER_ADMIN"));
        assertTrue(validator.isPrivilegedRole("ROLE_ADMIN"));
        assertTrue(validator.isPrivilegedRole("ROLE_SUPER_ADMIN"));
        assertFalse(validator.isPrivilegedRole("HOD"));
        assertFalse(validator.isPrivilegedRole("COUNSELOR"));
        assertFalse(validator.isPrivilegedRole((String) null));

        assertTrue(validator.isAdminRole("ADMIN"));
        assertTrue(validator.isAdminRole("ROLE_ADMIN"));
        assertFalse(validator.isAdminRole("SUPER_ADMIN"));

        assertTrue(validator.isSuperAdminRole("SUPER_ADMIN"));
        assertTrue(validator.isSuperAdminRole("ROLE_SUPER_ADMIN"));
        assertFalse(validator.isSuperAdminRole("ADMIN"));
    }

    // ==========================================
    // FEATURE FLAG DISABLED (allowAdminCreation = false)
    // ==========================================

    @Test
    @DisplayName("When flag is FALSE: Creating ADMIN throws AccessDeniedException with status message")
    void testFlagFalse_CreateAdmin_ThrowsAccessDenied() {
        properties.setAllowAdminCreation(false);
        authenticateAs("ROLE_SUPER_ADMIN");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                validator.validateRoleAssignment(List.of("ADMIN"), Collections.emptySet())
        );
        assertEquals(UserSecurityValidator.ADMIN_CREATION_DISABLED_MESSAGE, ex.getMessage());
    }

    @Test
    @DisplayName("When flag is FALSE: Creating SUPER_ADMIN throws AccessDeniedException with status message")
    void testFlagFalse_CreateSuperAdmin_ThrowsAccessDenied() {
        properties.setAllowAdminCreation(false);
        authenticateAs("ROLE_SUPER_ADMIN");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                validator.validateRoleAssignment(List.of("SUPER_ADMIN"), Collections.emptySet())
        );
        assertEquals(UserSecurityValidator.ADMIN_CREATION_DISABLED_MESSAGE, ex.getMessage());
    }

    @Test
    @DisplayName("When flag is FALSE: Creating HOD and COUNSELOR is permitted")
    void testFlagFalse_CreateHodAndCounselor_Permitted() {
        properties.setAllowAdminCreation(false);
        authenticateAs("ROLE_SUPER_ADMIN");

        assertDoesNotThrow(() ->
                validator.validateRoleAssignment(List.of("HOD"), Collections.emptySet())
        );
        assertDoesNotThrow(() ->
                validator.validateRoleAssignment(List.of("COUNSELOR"), Collections.emptySet())
        );
    }

    @Test
    @DisplayName("When flag is FALSE: Updating existing ADMIN without role change is permitted")
    void testFlagFalse_ExistingAdminUpdate_Permitted() {
        properties.setAllowAdminCreation(false);
        authenticateAs("ROLE_SUPER_ADMIN");

        assertDoesNotThrow(() ->
                validator.validateRoleAssignment(List.of("ADMIN"), Set.of("ADMIN"))
        );
        assertDoesNotThrow(() ->
                validator.validateRoleAssignment(List.of("SUPER_ADMIN"), Set.of("SUPER_ADMIN"))
        );
    }

    @Test
    @DisplayName("When flag is FALSE: Changing existing COUNSELOR to ADMIN is rejected")
    void testFlagFalse_PromotingCounselorToAdmin_ThrowsAccessDenied() {
        properties.setAllowAdminCreation(false);
        authenticateAs("ROLE_SUPER_ADMIN");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                validator.validateRoleAssignment(List.of("ADMIN"), Set.of("COUNSELOR"))
        );
        assertEquals(UserSecurityValidator.ADMIN_CREATION_DISABLED_MESSAGE, ex.getMessage());
    }

    @Test
    @DisplayName("When flag is FALSE: Privileged roles are filtered out from role options")
    void testFlagFalse_RoleFiltering() {
        properties.setAllowAdminCreation(false);
        authenticateAs("ROLE_SUPER_ADMIN");

        assertTrue(validator.isRoleAllowedForCreation(hodRole));
        assertTrue(validator.isRoleAllowedForCreation(counselorRole));
        assertFalse(validator.isRoleAllowedForCreation(adminRole));
        assertFalse(validator.isRoleAllowedForCreation(superAdminRole));
    }

    // ==========================================
    // FEATURE FLAG ENABLED (allowAdminCreation = true)
    // ==========================================

    @Test
    @DisplayName("When flag is TRUE: SUPER_ADMIN caller can assign SUPER_ADMIN and ADMIN")
    void testFlagTrue_SuperAdminCaller_AllowedAll() {
        properties.setAllowAdminCreation(true);
        authenticateAs("ROLE_SUPER_ADMIN");

        assertDoesNotThrow(() ->
                validator.validateRoleAssignment(List.of("SUPER_ADMIN"), Collections.emptySet())
        );
        assertDoesNotThrow(() ->
                validator.validateRoleAssignment(List.of("ADMIN"), Collections.emptySet())
        );
        assertDoesNotThrow(() ->
                validator.validateRoleAssignment(List.of("HOD"), Collections.emptySet())
        );
        assertDoesNotThrow(() ->
                validator.validateRoleAssignment(List.of("COUNSELOR"), Collections.emptySet())
        );

        assertTrue(validator.isRoleAllowedForCreation(superAdminRole));
        assertTrue(validator.isRoleAllowedForCreation(adminRole));
        assertTrue(validator.isRoleAllowedForCreation(hodRole));
        assertTrue(validator.isRoleAllowedForCreation(counselorRole));
    }

    @Test
    @DisplayName("When flag is TRUE: ADMIN caller can assign ADMIN, but cannot assign SUPER_ADMIN")
    void testFlagTrue_AdminCaller_PrivilegeEscalationPrevented() {
        properties.setAllowAdminCreation(true);
        authenticateAs("ROLE_ADMIN");

        assertDoesNotThrow(() ->
                validator.validateRoleAssignment(List.of("ADMIN"), Collections.emptySet())
        );
        assertDoesNotThrow(() ->
                validator.validateRoleAssignment(List.of("HOD"), Collections.emptySet())
        );

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                validator.validateRoleAssignment(List.of("SUPER_ADMIN"), Collections.emptySet())
        );
        assertEquals(UserSecurityValidator.SUPER_ADMIN_RESTRICTED_MESSAGE, ex.getMessage());

        assertFalse(validator.isRoleAllowedForCreation(superAdminRole));
        assertTrue(validator.isRoleAllowedForCreation(adminRole));
        assertTrue(validator.isRoleAllowedForCreation(hodRole));
        assertTrue(validator.isRoleAllowedForCreation(counselorRole));
    }

    @Test
    @DisplayName("When flag is TRUE: Non-privileged caller cannot assign ADMIN or SUPER_ADMIN")
    void testFlagTrue_CounselorCaller_CannotAssignPrivilegedRoles() {
        properties.setAllowAdminCreation(true);
        authenticateAs("ROLE_COUNSELOR");

        AccessDeniedException exAdmin = assertThrows(AccessDeniedException.class, () ->
                validator.validateRoleAssignment(List.of("ADMIN"), Collections.emptySet())
        );
        assertEquals(UserSecurityValidator.ADMIN_RESTRICTED_MESSAGE, exAdmin.getMessage());

        AccessDeniedException exSuper = assertThrows(AccessDeniedException.class, () ->
                validator.validateRoleAssignment(List.of("SUPER_ADMIN"), Collections.emptySet())
        );
        assertEquals(UserSecurityValidator.SUPER_ADMIN_RESTRICTED_MESSAGE, exSuper.getMessage());

        assertFalse(validator.isRoleAllowedForCreation(superAdminRole));
        assertFalse(validator.isRoleAllowedForCreation(adminRole));
        assertTrue(validator.isRoleAllowedForCreation(hodRole));
        assertTrue(validator.isRoleAllowedForCreation(counselorRole));
    }
}
