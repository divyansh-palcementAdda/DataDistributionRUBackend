package com.app.datadistribution.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.HodAccessType;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.repository.BoardRepository;
import com.app.datadistribution.repository.DashboardCardRepository;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.GradeRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.LeadStatusSentimentRepository;
import com.app.datadistribution.repository.PermissionRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.IDashboardCardPermissionService;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatabaseSeederTest {

    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LeadStatusSentimentRepository leadStatusSentimentRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private DashboardCardRepository dashboardCardRepository;
    @Mock
    private IDashboardCardPermissionService dashboardCardPermissionService;
    @Mock
    private LeadSourceRepository leadSourceRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private DatabaseSeeder seeder;

    private final Map<String, Role> roleDb = new HashMap<>();
    private final Map<String, Department> deptDb = new HashMap<>();
    private final Map<String, User> userDb = new HashMap<>();
    private final Map<String, Permission> permDb = new HashMap<>();

    @BeforeEach
    void setUp() {
        roleDb.clear();
        deptDb.clear();
        userDb.clear();
        permDb.clear();

        ReflectionTestUtils.setField(seeder, "defaultSuperAdminPassword", "Admin@123");
        ReflectionTestUtils.setField(seeder, "defaultAdminPassword", "Admin@123");
        ReflectionTestUtils.setField(seeder, "defaultHodPassword", "Hod@123");
        ReflectionTestUtils.setField(seeder, "defaultCounselorPassword", "Counselor@123");

        lenient().when(permissionRepository.findByName(anyString())).thenAnswer(inv -> Optional.ofNullable(permDb.get(inv.getArgument(0))));
        lenient().when(permissionRepository.save(any(Permission.class))).thenAnswer(inv -> {
            Permission p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            permDb.put(p.getName(), p);
            return p;
        });
        lenient().when(permissionRepository.findAll()).thenAnswer(inv -> new ArrayList<>(permDb.values()));

        lenient().when(roleRepository.findByName(anyString())).thenAnswer(inv -> Optional.ofNullable(roleDb.get(inv.getArgument(0))));
        lenient().when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            roleDb.put(r.getName(), r);
            return r;
        });
        lenient().when(roleRepository.findAll()).thenAnswer(inv -> new ArrayList<>(roleDb.values()));

        lenient().when(departmentRepository.findByCodeIgnoreCaseAndIsDeletedFalse(anyString())).thenAnswer(inv -> Optional.ofNullable(deptDb.get(((String) inv.getArgument(0)).toUpperCase())));
        lenient().when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> {
            Department d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            deptDb.put(d.getCode().toUpperCase(), d);
            return d;
        });

        lenient().when(userRepository.findByUsername(anyString())).thenAnswer(inv -> Optional.ofNullable(userDb.get(inv.getArgument(0))));
        lenient().when(userRepository.existsByEmail(anyString())).thenAnswer(inv -> userDb.values().stream().anyMatch(u -> Objects.equals(inv.getArgument(0), u.getEmail())));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            userDb.put(u.getUsername(), u);
            return u;
        });
        lenient().when(userRepository.findAll()).thenAnswer(inv -> new ArrayList<>(userDb.values()));

        lenient().when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "encoded_" + inv.getArgument(0));
    }

    @Test
    void testSeedPermissions_CreatesAllEnumPermissions() {
        seeder.run();

        assertEquals(PermissionType.values().length, permDb.size(),
                "All PermissionType values should be seeded in permission repository");
        for (PermissionType pt : PermissionType.values()) {
            assertTrue(permDb.containsKey(pt.name()), "Missing permission: " + pt.name());
        }
    }

    @Test
    void testSeedRoles_CreatesSuperAdminAdminHodCounselor() {
        seeder.run();

        Role superAdmin = roleDb.get(RoleType.SUPER_ADMIN.name());
        Role admin = roleDb.get(RoleType.ADMIN.name());
        Role hod = roleDb.get(RoleType.HOD.name());
        Role counselor = roleDb.get(RoleType.COUNSELOR.name());

        assertNotNull(superAdmin, "SUPER_ADMIN role must be created");
        assertNotNull(admin, "ADMIN role must be created");
        assertNotNull(hod, "HOD role must be created");
        assertNotNull(counselor, "COUNSELOR role must be created");

        assertEquals(PermissionType.values().length, superAdmin.getPermissions().size(),
                "SUPER_ADMIN should have all permissions");
        assertTrue(admin.getPermissions().size() > 0, "ADMIN should have permissions");
        assertTrue(hod.getPermissions().size() > 0, "HOD should have permissions");
        assertTrue(counselor.getPermissions().size() > 0, "COUNSELOR should have permissions");
    }

    @Test
    void testSeedRoles_PreservesExistingDynamicPermissionsOnRestart() {
        // Pre-create role with custom dynamic permission
        Permission defaultPerm = Permission.builder().name(PermissionType.LEAD_READ.name()).active(true).build();
        Permission customDynamicPerm = Permission.builder().name("CUSTOM_DYNAMIC_PERMISSION").active(true).build();
        permDb.put(defaultPerm.getName(), defaultPerm);
        permDb.put(customDynamicPerm.getName(), customDynamicPerm);

        Set<Permission> existingPermissions = new HashSet<>();
        existingPermissions.add(defaultPerm);
        existingPermissions.add(customDynamicPerm);

        Role existingAdminRole = Role.builder()
                .name(RoleType.ADMIN.name())
                .description("Administrator Role")
                .active(true)
                .permissions(existingPermissions)
                .build();
        roleDb.put(RoleType.ADMIN.name(), existingAdminRole);

        seeder.run();

        Role updatedAdminRole = roleDb.get(RoleType.ADMIN.name());
        assertTrue(updatedAdminRole.getPermissions().contains(customDynamicPerm),
                "Custom dynamic permissions manually assigned to existing roles must be preserved on restart");
    }

    @Test
    void testSeedDepartments_CreatesDefaultDepartmentsWithoutDuplicates() {
        seeder.run();

        assertTrue(deptDb.containsKey("ADM"), "Department ADM should be seeded");
        assertTrue(deptDb.containsKey("MKT"), "Department MKT should be seeded");
        assertEquals("Admissions", deptDb.get("ADM").getName());
        assertEquals("Marketing", deptDb.get("MKT").getName());

        // Repeated run should not duplicate
        int countBefore = deptDb.size();
        seeder.run();
        assertEquals(countBefore, deptDb.size());
    }

    @Test
    void testMigrateLegacyUserRoleToCounselor_MigratesAssignedUsersAndSetsDefaultDepartment() {
        Role legacyUserRole = Role.builder().name(RoleType.USER.name()).active(true).build();
        legacyUserRole.setId(UUID.randomUUID());
        roleDb.put(RoleType.USER.name(), legacyUserRole);

        User userWithLegacyRole = User.builder()
                .username("legacy_user")
                .email("legacy@test.com")
                .roles(new HashSet<>(List.of(legacyUserRole)))
                .departments(new HashSet<>())
                .active(true)
                .build();
        userWithLegacyRole.setId(UUID.randomUUID());
        userDb.put(userWithLegacyRole.getUsername(), userWithLegacyRole);

        seeder.run();

        Role counselorRole = roleDb.get(RoleType.COUNSELOR.name());
        assertNotNull(counselorRole);

        User migratedUser = userDb.get("legacy_user");
        assertFalse(migratedUser.getRoles().contains(legacyUserRole), "Legacy USER role should be removed");
        assertTrue(migratedUser.getRoles().contains(counselorRole), "COUNSELOR role should be assigned");
        assertTrue(migratedUser.getDepartments().stream().anyMatch(d -> "ADM".equals(d.getCode())),
                "Default department should be assigned if missing");
        assertFalse(legacyUserRole.isActive(), "Legacy USER role entity should be deactivated");
    }

    @Test
    void testSeedUsers_CreatesDefaultUsersWithAppropriateRolesAndDepartments() {
        seeder.run();

        User superAdmin = userDb.get("superadmin");
        User admin = userDb.get("admin");
        User hod = userDb.get("hod");
        User counselor = userDb.get("counselor");

        assertNotNull(superAdmin, "superadmin user should be seeded");
        assertNotNull(admin, "admin user should be seeded");
        assertNotNull(hod, "hod user should be seeded");
        assertNotNull(counselor, "counselor user should be seeded");

        // Department mapping assertions
        assertTrue(superAdmin.getDepartments() == null || superAdmin.getDepartments().isEmpty(),
                "SUPER_ADMIN must not have department mapping");
        assertTrue(admin.getDepartments() == null || admin.getDepartments().isEmpty(),
                "ADMIN must not have department mapping");

        assertTrue(hod.getDepartments() != null && hod.getDepartments().stream().anyMatch(d -> "ADM".equals(d.getCode())),
                "HOD must have department mapping");
        assertEquals(HodAccessType.FULL_ACCESS, hod.getHodAccessType(),
                "HOD must have FULL_ACCESS by default");

        assertTrue(counselor.getDepartments() != null && counselor.getDepartments().stream().anyMatch(d -> "ADM".equals(d.getCode())),
                "COUNSELOR must have department mapping");
    }

    @Test
    void testSeedUsers_DoesNotOverwriteExistingUsersOrPasswords() {
        User existingAdmin = User.builder()
                .username("admin")
                .email("admin@datadistribution.com")
                .password("existing_custom_password_hash")
                .active(true)
                .build();
        userDb.put("admin", existingAdmin);

        seeder.run();

        User adminAfterSeed = userDb.get("admin");
        assertEquals("existing_custom_password_hash", adminAfterSeed.getPassword(),
                "Existing user password must not be overwritten by seeder");
    }
}
