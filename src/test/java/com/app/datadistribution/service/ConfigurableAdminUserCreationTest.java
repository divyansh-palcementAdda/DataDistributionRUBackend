package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.app.datadistribution.config.UserManagementProperties;
import com.app.datadistribution.dto.dropdown.DropdownOptionResponse;
import com.app.datadistribution.dto.user.UserCreationOptionsResponse;
import com.app.datadistribution.dto.user.UserRequest;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.dto.user.UserUpdateRequest;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.mapper.UserMapper;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.security.UserSecurityValidator;
import com.app.datadistribution.service.impl.DropdownServiceImpl;
import com.app.datadistribution.service.impl.UserServiceImpl;
import com.app.datadistribution.service.interfaces.IActivityLogService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigurableAdminUserCreationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private IActivityLogService activityLogService;

    private UserManagementProperties properties;
    private UserSecurityValidator validator;
    private UserServiceImpl userService;
    private DropdownServiceImpl dropdownService;

    private Role superAdminRole;
    private Role adminRole;
    private Role hodRole;
    private Role counselorRole;

    @BeforeEach
    void setUp() {
        properties = new UserManagementProperties();
        validator = new UserSecurityValidator(properties);

        userService = new UserServiceImpl(
                userRepository,
                roleRepository,
                departmentRepository,
                passwordEncoder,
                userMapper,
                activityLogService,
                validator,
                properties
        );

        dropdownService = new DropdownServiceImpl(
                userRepository,
                departmentRepository,
                null, null, null, null, null, null, null, null,
                roleRepository,
                null, null, null, null,
                validator
        );

        superAdminRole = Role.builder().name(RoleType.SUPER_ADMIN.name()).active(true).build();
        superAdminRole.setId(UUID.randomUUID());

        adminRole = Role.builder().name(RoleType.ADMIN.name()).active(true).build();
        adminRole.setId(UUID.randomUUID());

        hodRole = Role.builder().name(RoleType.HOD.name()).active(true).build();
        hodRole.setId(UUID.randomUUID());

        counselorRole = Role.builder().name(RoleType.COUNSELOR.name()).active(true).build();
        counselorRole.setId(UUID.randomUUID());

        when(roleRepository.findAll()).thenReturn(List.of(superAdminRole, adminRole, hodRole, counselorRole));
        when(roleRepository.findByName(RoleType.SUPER_ADMIN.name())).thenReturn(Optional.of(superAdminRole));
        when(roleRepository.findByName(RoleType.ADMIN.name())).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByName(RoleType.HOD.name())).thenReturn(Optional.of(hodRole));
        when(roleRepository.findByName(RoleType.COUNSELOR.name())).thenReturn(Optional.of(counselorRole));
        when(roleRepository.findByIdAndIsDeletedFalse(superAdminRole.getId())).thenReturn(Optional.of(superAdminRole));
        when(roleRepository.findByIdAndIsDeletedFalse(adminRole.getId())).thenReturn(Optional.of(adminRole));
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
    @DisplayName("When flag is FALSE: /api/dropdowns/roles returns only HOD and COUNSELOR")
    void testGetRolesDropdown_FlagFalse_ExcludesPrivilegedRoles() {
        properties.setAllowAdminCreation(false);
        authenticateAs("ROLE_SUPER_ADMIN");

        List<DropdownOptionResponse> roles = dropdownService.getRolesDropdown(null);

        assertEquals(2, roles.size());
        assertTrue(roles.stream().anyMatch(r -> r.getName().equals(RoleType.HOD.name())));
        assertTrue(roles.stream().anyMatch(r -> r.getName().equals(RoleType.COUNSELOR.name())));
        assertFalse(roles.stream().anyMatch(r -> r.getName().equals(RoleType.ADMIN.name())));
        assertFalse(roles.stream().anyMatch(r -> r.getName().equals(RoleType.SUPER_ADMIN.name())));
    }

    @Test
    @DisplayName("When flag is TRUE: /api/dropdowns/roles returns all roles for Super Admin")
    void testGetRolesDropdown_FlagTrue_SuperAdmin_ReturnsAllRoles() {
        properties.setAllowAdminCreation(true);
        authenticateAs("ROLE_SUPER_ADMIN");

        List<DropdownOptionResponse> roles = dropdownService.getRolesDropdown(null);

        assertEquals(4, roles.size());
        assertTrue(roles.stream().anyMatch(r -> r.getName().equals(RoleType.SUPER_ADMIN.name())));
        assertTrue(roles.stream().anyMatch(r -> r.getName().equals(RoleType.ADMIN.name())));
        assertTrue(roles.stream().anyMatch(r -> r.getName().equals(RoleType.HOD.name())));
        assertTrue(roles.stream().anyMatch(r -> r.getName().equals(RoleType.COUNSELOR.name())));
    }

    @Test
    @DisplayName("When flag is TRUE: /api/dropdowns/roles returns ADMIN, HOD, COUNSELOR for Admin caller")
    void testGetRolesDropdown_FlagTrue_Admin_ExcludesSuperAdmin() {
        properties.setAllowAdminCreation(true);
        authenticateAs("ROLE_ADMIN");

        List<DropdownOptionResponse> roles = dropdownService.getRolesDropdown(null);

        assertEquals(3, roles.size());
        assertFalse(roles.stream().anyMatch(r -> r.getName().equals(RoleType.SUPER_ADMIN.name())));
        assertTrue(roles.stream().anyMatch(r -> r.getName().equals(RoleType.ADMIN.name())));
        assertTrue(roles.stream().anyMatch(r -> r.getName().equals(RoleType.HOD.name())));
        assertTrue(roles.stream().anyMatch(r -> r.getName().equals(RoleType.COUNSELOR.name())));
    }

    @Test
    @DisplayName("When flag is FALSE: User creation options returns allowAdminCreation=false and only non-privileged roles")
    void testGetUserCreationOptions_FlagFalse() {
        properties.setAllowAdminCreation(false);
        authenticateAs("ROLE_SUPER_ADMIN");

        UserCreationOptionsResponse response = userService.getUserCreationOptions();

        assertFalse(response.isAllowAdminCreation());
        assertEquals(2, response.getRoles().size());
        assertTrue(response.getRoles().stream().anyMatch(r -> r.getCode().equals(RoleType.HOD.name())));
        assertTrue(response.getRoles().stream().anyMatch(r -> r.getCode().equals(RoleType.COUNSELOR.name())));
    }

    @Test
    @DisplayName("When flag is FALSE: Creating ADMIN user via createUser is rejected with AccessDeniedException")
    void testCreateUser_Admin_FlagFalse_Rejected() {
        properties.setAllowAdminCreation(false);
        authenticateAs("ROLE_SUPER_ADMIN");

        UserRequest request = UserRequest.builder()
                .username("newadmin")
                .email("admin@test.com")
                .password("Pass123!")
                .roles(Set.of("ADMIN"))
                .build();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                userService.createUser(request)
        );
        assertEquals(UserSecurityValidator.ADMIN_CREATION_DISABLED_MESSAGE, ex.getMessage());
    }

    @Test
    @DisplayName("When flag is FALSE: Assigning SUPER_ADMIN role via assignRole is rejected")
    void testAssignRole_SuperAdmin_FlagFalse_Rejected() {
        properties.setAllowAdminCreation(false);
        authenticateAs("ROLE_SUPER_ADMIN");

        UUID userId = UUID.randomUUID();
        User existingUser = User.builder()
                .roles(new HashSet<>(Set.of(counselorRole)))
                .build();
        existingUser.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                userService.assignRole(userId, superAdminRole.getId())
        );
        assertEquals(UserSecurityValidator.ADMIN_CREATION_DISABLED_MESSAGE, ex.getMessage());
    }

    @Test
    @DisplayName("When flag is FALSE: Updating existing Counselor user to Admin is rejected")
    void testUpdateUser_CounselorToAdmin_FlagFalse_Rejected() {
        properties.setAllowAdminCreation(false);
        authenticateAs("ROLE_SUPER_ADMIN");

        UUID userId = UUID.randomUUID();
        User existingUser = User.builder()
                .username("counselor1")
                .email("c1@test.com")
                .roles(new HashSet<>(Set.of(counselorRole)))
                .build();
        existingUser.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        UserUpdateRequest request = UserUpdateRequest.builder()
                .username("counselor1")
                .email("c1@test.com")
                .roles(Set.of("ADMIN"))
                .build();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                userService.updateUser(userId, request)
        );
        assertEquals(UserSecurityValidator.ADMIN_CREATION_DISABLED_MESSAGE, ex.getMessage());
    }

    @Test
    @DisplayName("When flag is FALSE: Updating existing Admin user profile without role escalation succeeds")
    void testUpdateUser_ExistingAdmin_FlagFalse_Succeeds() {
        properties.setAllowAdminCreation(false);
        authenticateAs("ROLE_SUPER_ADMIN");

        UUID userId = UUID.randomUUID();
        User existingUser = User.builder()
                .username("admin1")
                .email("admin1@test.com")
                .roles(new HashSet<>(Set.of(adminRole)))
                .tokenVersion(1L)
                .build();
        existingUser.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toDto(any(User.class))).thenReturn(UserResponse.builder().username("admin1").build());

        UserUpdateRequest request = UserUpdateRequest.builder()
                .username("admin1")
                .email("admin1@test.com")
                .firstName("Updated")
                .roles(Set.of("ADMIN"))
                .build();

        assertDoesNotThrow(() -> userService.updateUser(userId, request));
    }
}
