package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.app.datadistribution.dto.user.UserRequest;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.dto.user.UserUpdateRequest;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.HodAccessType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.mapper.UserMapper;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.impl.UserServiceImpl;
import com.app.datadistribution.service.interfaces.IActivityLogService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceDepartmentValidationTest {

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

    @InjectMocks
    private UserServiceImpl userService;

    private Role superAdminRole;
    private Role adminRole;
    private Role hodRole;
    private Role counselorRole;
    private Department dept;

    @BeforeEach
    void setUp() {
        superAdminRole = Role.builder().name(RoleType.SUPER_ADMIN.name()).build();
        adminRole = Role.builder().name(RoleType.ADMIN.name()).build();
        hodRole = Role.builder().name(RoleType.HOD.name()).build();
        counselorRole = Role.builder().name(RoleType.COUNSELOR.name()).build();

        dept = Department.builder().name("Admissions").code("ADM").active(true).build();
        dept.setId(UUID.randomUUID());

        when(roleRepository.findByName(RoleType.SUPER_ADMIN.name())).thenReturn(Optional.of(superAdminRole));
        when(roleRepository.findByName(RoleType.ADMIN.name())).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByName(RoleType.HOD.name())).thenReturn(Optional.of(hodRole));
        when(roleRepository.findByName(RoleType.COUNSELOR.name())).thenReturn(Optional.of(counselorRole));

        when(departmentRepository.findById(dept.getId())).thenReturn(Optional.of(dept));
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(User.class))).thenReturn(UserResponse.builder().username("testuser").build());
    }

    @Test
    void testCreateAdminWithDepartment_ThrowsBadRequestException() {
        UserRequest request = UserRequest.builder()
                .username("new_admin")
                .email("new_admin@test.com")
                .password("Password@123")
                .firstName("Admin")
                .lastName("User")
                .roles(Set.of(RoleType.ADMIN.name()))
                .departmentIds(List.of(dept.getId()))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.createUser(request));
        assertTrue(ex.getMessage().contains("Admin and SUPER_ADMIN users cannot be assigned to specific departments"));
    }

    @Test
    void testCreateSuperAdminWithDepartment_ThrowsBadRequestException() {
        UserRequest request = UserRequest.builder()
                .username("new_superadmin")
                .email("new_superadmin@test.com")
                .password("Password@123")
                .firstName("Super")
                .lastName("Admin")
                .roles(Set.of(RoleType.SUPER_ADMIN.name()))
                .departmentIds(List.of(dept.getId()))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.createUser(request));
        assertTrue(ex.getMessage().contains("Admin and SUPER_ADMIN users cannot be assigned to specific departments"));
    }

    @Test
    void testCreateHodWithoutDepartment_ThrowsBadRequestException() {
        UserRequest request = UserRequest.builder()
                .username("new_hod")
                .email("new_hod@test.com")
                .password("Password@123")
                .firstName("Hod")
                .lastName("User")
                .roles(Set.of(RoleType.HOD.name()))
                .departmentIds(Collections.emptyList())
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.createUser(request));
        assertTrue(ex.getMessage().contains("Department mapping is required for HOD and COUNSELOR roles"));
    }

    @Test
    void testCreateCounselorWithoutDepartment_ThrowsBadRequestException() {
        UserRequest request = UserRequest.builder()
                .username("new_counselor")
                .email("new_counselor@test.com")
                .password("Password@123")
                .firstName("Counselor")
                .lastName("User")
                .roles(Set.of(RoleType.COUNSELOR.name()))
                .departmentIds(Collections.emptyList())
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.createUser(request));
        assertTrue(ex.getMessage().contains("Department mapping is required for HOD and COUNSELOR roles"));
    }

    @Test
    void testCreateHodWithDepartment_DefaultsToFullAccessWhenNull() throws Exception {
        UserRequest request = UserRequest.builder()
                .username("valid_hod")
                .email("valid_hod@test.com")
                .password("Password@123")
                .firstName("Hod")
                .lastName("User")
                .roles(Set.of(RoleType.HOD.name()))
                .departmentIds(List.of(dept.getId()))
                .build();

        userService.createUser(request);

        verify(userRepository).save(argThat(user ->
                user.getRoles().contains(hodRole) &&
                user.getDepartments().contains(dept) &&
                user.getHodAccessType() == HodAccessType.FULL_ACCESS
        ));
    }

    @Test
    void testCreateCounselorWithDepartment_Succeeds() throws Exception {
        UserRequest request = UserRequest.builder()
                .username("valid_counselor")
                .email("valid_counselor@test.com")
                .password("Password@123")
                .firstName("Counselor")
                .lastName("User")
                .roles(Set.of(RoleType.COUNSELOR.name()))
                .departmentIds(List.of(dept.getId()))
                .build();

        userService.createUser(request);

        verify(userRepository).save(argThat(user ->
                user.getRoles().contains(counselorRole) &&
                user.getDepartments().contains(dept)
        ));
    }
}
