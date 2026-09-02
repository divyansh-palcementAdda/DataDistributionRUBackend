package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.dashboard.*;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.repository.ActivityLogRepository;
import com.app.datadistribution.repository.DashboardCardRepository;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.DashboardServiceImpl;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

@ExtendWith(MockitoExtension.class)
class DashboardOperationalUserExclusionTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;
    @Mock
    private DashboardCardRepository dashboardCardRepository;
    @Mock
    private com.app.datadistribution.repository.UserDashboardCardPreferenceRepository userPreferenceRepository;
    @Mock
    private com.app.datadistribution.repository.DashboardAnalyticsRepository dashboardAnalyticsRepository;
    @Mock
    private jakarta.persistence.EntityManager entityManager;
    @Mock
    private IUserDataScopeService dataScopeService;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private User superAdmin;
    private User stdAdmin;
    private User hodUserDeptA;
    private User hodUserDeptB;
    private User counselor1DeptA;
    private User counselor2DeptB;

    private Department deptA;
    private Department deptB;

    private UserDataScope systemScope;
    private UserDataScope hodDeptAScope;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dashboardService, "lowDataUserThreshold", 10);
        ReflectionTestUtils.setField(dashboardService, "cutoffTimeStr", "11:00");

        deptA = Department.builder().name("Department A").active(true).build();
        deptA.setId(UUID.randomUUID());

        deptB = Department.builder().name("Department B").active(true).build();
        deptB.setId(UUID.randomUUID());

        Role superAdminRole = Role.builder().name(RoleType.SUPER_ADMIN.name()).build();
        Role adminRole = Role.builder().name(RoleType.ADMIN.name()).build();
        Role hodRole = Role.builder().name(RoleType.HOD.name()).build();
        Role counselorRole = Role.builder().name(RoleType.COUNSELOR.name()).build();

        superAdmin = User.builder().username("superadmin").firstName("Super").lastName("Admin").email("superadmin@test.com")
                .active(true).roles(Set.of(superAdminRole)).build();
        superAdmin.setId(UUID.randomUUID());

        stdAdmin = User.builder().username("admin").firstName("Standard").lastName("Admin").email("admin@test.com")
                .active(true).roles(Set.of(adminRole)).build();
        stdAdmin.setId(UUID.randomUUID());

        hodUserDeptA = User.builder().username("hod_a").firstName("Head").lastName("DeptA").email("hod_a@test.com")
                .active(true).roles(Set.of(hodRole)).departments(Set.of(deptA)).build();
        hodUserDeptA.setId(UUID.randomUUID());

        hodUserDeptB = User.builder().username("hod_b").firstName("Head").lastName("DeptB").email("hod_b@test.com")
                .active(true).roles(Set.of(hodRole)).departments(Set.of(deptB)).build();
        hodUserDeptB.setId(UUID.randomUUID());

        counselor1DeptA = User.builder().username("counselor_a").firstName("Abhishek").lastName("Sharma").email("abhishek@test.com")
                .active(true).roles(Set.of(counselorRole)).departments(Set.of(deptA)).build();
        counselor1DeptA.setId(UUID.randomUUID());

        counselor2DeptB = User.builder().username("counselor_b").firstName("Lead").lastName("Counsellor").email("lead_counselor@test.com")
                .active(true).roles(Set.of(counselorRole)).departments(Set.of(deptB)).build();
        counselor2DeptB.setId(UUID.randomUUID());

        systemScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .userId(superAdmin.getId())
                .currentUser(superAdmin)
                .build();

        hodDeptAScope = UserDataScope.builder()
                .scopeType(ScopeType.DEPARTMENT)
                .isAdmin(false)
                .isHod(true)
                .userId(hodUserDeptA.getId())
                .currentUser(hodUserDeptA)
                .departmentIds(Set.of(deptA.getId()))
                .departmentUserIds(Set.of(hodUserDeptA.getId(), counselor1DeptA.getId()))
                .build();

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("superadmin");
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(superAdmin));

        lenient().when(dashboardAnalyticsRepository.fetchTotalMatchingLeads(any(), any())).thenReturn(0L);
        lenient().when(dashboardCardRepository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(Collections.emptyList());
        lenient().when(userPreferenceRepository.findByUserId(any())).thenReturn(Collections.emptyList());

        jakarta.persistence.criteria.CriteriaBuilder cb = mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        jakarta.persistence.criteria.CriteriaQuery cq = mock(jakarta.persistence.criteria.CriteriaQuery.class);
        jakarta.persistence.criteria.Root root = mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.Path path = mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.TypedQuery tq = mock(jakarta.persistence.TypedQuery.class);
        jakarta.persistence.criteria.Predicate pred = mock(jakarta.persistence.criteria.Predicate.class);

        lenient().when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        lenient().when(cb.createQuery(any())).thenReturn(cq);
        lenient().when(cq.from(any(Class.class))).thenReturn(root);
        lenient().when(root.get(anyString())).thenReturn(path);
        lenient().when(path.get(anyString())).thenReturn(path);
        lenient().when(cb.equal(any(), any())).thenReturn(pred);
        lenient().when(cb.between(any(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(pred);
        lenient().when(cb.or(any(jakarta.persistence.criteria.Predicate[].class))).thenReturn(pred);
        lenient().when(cb.and(any(jakarta.persistence.criteria.Predicate[].class))).thenReturn(pred);
        lenient().when(cb.countDistinct(any())).thenReturn(mock(jakarta.persistence.criteria.Expression.class));
        lenient().when(cq.select(any())).thenReturn(cq);
        lenient().when(cq.where(any(jakarta.persistence.criteria.Predicate[].class))).thenReturn(cq);
        lenient().when(entityManager.createQuery(cq)).thenReturn(tq);
        lenient().when(tq.getSingleResult()).thenReturn(0L);
    }

    @Test
    @DisplayName("TEST 1: Counsellors logged today counts only HOD and Counsellor, excluding Admin and Super Admin")
    void testCountCounsellorsLoggedToday_ExcludesAdminAndSuperAdmin() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        superAdmin.setLastLogin(now);
        stdAdmin.setLastLogin(now);
        hodUserDeptA.setLastLogin(now);
        counselor1DeptA.setLastLogin(now);
        counselor2DeptB.setLastLogin(null); // Not logged in

        lenient().when(dataScopeService.getScopeForCurrentUser(any(DashboardAnalyticsFilterRequest.class))).thenReturn(systemScope);
        lenient().when(userRepository.findAll()).thenReturn(List.of(superAdmin, stdAdmin, hodUserDeptA, counselor1DeptA, counselor2DeptB));

        DashboardSummaryDTO summary = dashboardService.getDashboardSummary(new DashboardAnalyticsFilterRequest());

        // hodUserDeptA + counselor1DeptA = 2. superAdmin and stdAdmin MUST NOT be counted.
        assertEquals(2L, summary.getCounsellorsLoggedToday());
    }

    @Test
    @DisplayName("TEST 2: Counsellors currently working counts only HOD and Counsellor, excluding Admin and Super Admin")
    void testCountCounsellorsWorking_ExcludesAdminAndSuperAdmin() throws Exception {
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);
        superAdmin.setLastLogin(twoHoursAgo);
        stdAdmin.setLastLogin(twoHoursAgo);
        hodUserDeptA.setLastLogin(twoHoursAgo);
        counselor1DeptA.setLastLogin(twoHoursAgo);
        counselor2DeptB.setLastLogin(LocalDateTime.now().minusHours(12)); // Working session expired

        lenient().when(dataScopeService.getScopeForCurrentUser(any(DashboardAnalyticsFilterRequest.class))).thenReturn(systemScope);
        lenient().when(userRepository.findAll()).thenReturn(List.of(superAdmin, stdAdmin, hodUserDeptA, counselor1DeptA, counselor2DeptB));

        DashboardSummaryDTO summary = dashboardService.getDashboardSummary(new DashboardAnalyticsFilterRequest());

        // hodUserDeptA + counselor1DeptA = 2. superAdmin and stdAdmin MUST NOT be counted.
        assertEquals(2L, summary.getCounsellorsCurrentlyWorking());
    }

    @Test
    @DisplayName("TEST 3: Low data count counts only HOD and Counsellor, excluding Admin and Super Admin")
    void testCountLowDataUsers_ExcludesAdminAndSuperAdmin() throws Exception {
        lenient().when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        lenient().when(userRepository.findAll()).thenReturn(List.of(superAdmin, stdAdmin, hodUserDeptA, counselor1DeptA, counselor2DeptB));

        // All 5 users have 0 remaining leads (< threshold 10)
        lenient().when(leadRepository.findUnavailedLeadCountsGroupedByUser()).thenReturn(List.of());
        lenient().when(leadRepository.findAllottedLeadCountsGroupedByUser()).thenReturn(List.of());

        long lowDataCount = dashboardService.countLowDataUsers();

        // Only hodUserDeptA, counselor1DeptA, counselor2DeptB are eligible (3 users)
        assertEquals(3L, lowDataCount);
    }

    @Test
    @DisplayName("TEST 4: Users not logged in counts only HOD and Counsellor, excluding Admin and Super Admin")
    void testCountUsersNotLoggedInToday_ExcludesAdminAndSuperAdmin() throws Exception {
        lenient().when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        lenient().when(userRepository.findAll()).thenReturn(List.of(superAdmin, stdAdmin, hodUserDeptA, counselor1DeptA, counselor2DeptB));
        lenient().when(activityLogRepository.findDailyLoginStatsGroupedByPerformedBy(any(), any())).thenReturn(new ArrayList<>());

        long notLoggedInCount = dashboardService.countUsersNotLoggedInToday();

        // 3 eligible users: hodUserDeptA, counselor1DeptA, counselor2DeptB
        assertEquals(3L, notLoggedInCount);
    }

    @Test
    @DisplayName("TEST 5: Follow-up users not logged in by 11 AM counts only HOD and Counsellor, excluding Admin and Super Admin")
    void testFollowUpUsersNotLoggedInBy11Am_ExcludesAdminAndSuperAdmin() throws Exception {
        lenient().when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        lenient().when(userRepository.findAll()).thenReturn(List.of(superAdmin, stdAdmin, hodUserDeptA, counselor1DeptA, counselor2DeptB));

        // Set cutoff to past time
        ReflectionTestUtils.setField(dashboardService, "cutoffTimeStr", "00:00");

        // All 5 users have follow-ups scheduled today
        List<Object[]> followUpCounts = List.of(
                new Object[]{superAdmin.getId(), 5L},
                new Object[]{stdAdmin.getId(), 5L},
                new Object[]{hodUserDeptA.getId(), 5L},
                new Object[]{counselor1DeptA.getId(), 5L},
                new Object[]{counselor2DeptB.getId(), 5L}
        );
        lenient().when(leadFollowUpRepository.countScheduledFollowUpsGroupedByUserBetween(any(), any())).thenReturn(followUpCounts);
        lenient().when(leadFollowUpRepository.findEarliestScheduledFollowUpGroupedByUserBetween(any(), any())).thenReturn(new ArrayList<>());
        lenient().when(activityLogRepository.findDailyLoginStatsGroupedByPerformedBy(any(), any())).thenReturn(new ArrayList<>());

        long count = dashboardService.countFollowUpUsersNotLoggedInBy11Am();

        // Only 3 eligible users should be counted
        assertEquals(3L, count);
    }

    @Test
    @DisplayName("TEST 6: Detail API /api/dashboard/users-not-logged-in returns ONLY eligible HOD and Counsellors")
    void testGetUsersNotLoggedInToday_DetailApiExcludesAdmins() throws Exception {
        lenient().when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        lenient().when(userRepository.findAll()).thenReturn(List.of(superAdmin, stdAdmin, hodUserDeptA, counselor1DeptA, counselor2DeptB));
        lenient().when(activityLogRepository.findDailyLoginStatsGroupedByPerformedBy(any(), any())).thenReturn(new ArrayList<>());

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        UserNotLoggedInPageResponseDTO response = dashboardService.getUsersNotLoggedInToday(pageRequest);

        assertNotNull(response);
        assertEquals(3, response.getTotalElements());
        assertEquals(3, response.getContent().size());

        List<UUID> returnedIds = response.getContent().stream().map(UserNotLoggedInSummaryDTO::getUserId).toList();
        assertTrue(returnedIds.contains(hodUserDeptA.getId()));
        assertTrue(returnedIds.contains(counselor1DeptA.getId()));
        assertTrue(returnedIds.contains(counselor2DeptB.getId()));
        assertFalse(returnedIds.contains(superAdmin.getId()));
        assertFalse(returnedIds.contains(stdAdmin.getId()));
    }

    @Test
    @DisplayName("TEST 7: Detail API /api/dashboard/low-data-users returns ONLY eligible HOD and Counsellors")
    void testGetLowDataUsers_DetailApiExcludesAdmins() throws Exception {
        lenient().when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        lenient().when(userRepository.findAll()).thenReturn(List.of(superAdmin, stdAdmin, hodUserDeptA, counselor1DeptA, counselor2DeptB));
        lenient().when(leadRepository.findUnavailedLeadCountsGroupedByUser()).thenReturn(List.of());
        lenient().when(leadRepository.findAllottedLeadCountsGroupedByUser()).thenReturn(List.of());

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        LowDataUserPageResponseDTO response = dashboardService.getLowDataUsers(pageRequest);

        assertNotNull(response);
        assertEquals(3, response.getTotalElements());

        List<UUID> returnedIds = response.getContent().stream().map(LowDataUserSummaryDTO::getUserId).toList();
        assertTrue(returnedIds.contains(hodUserDeptA.getId()));
        assertTrue(returnedIds.contains(counselor1DeptA.getId()));
        assertTrue(returnedIds.contains(counselor2DeptB.getId()));
        assertFalse(returnedIds.contains(superAdmin.getId()));
        assertFalse(returnedIds.contains(stdAdmin.getId()));
    }

    @Test
    @DisplayName("TEST 8: Detail API /api/dashboard/followup-users-not-logged-in-11am returns ONLY eligible HOD and Counsellors")
    void testGetFollowUpUsersNotLoggedInBy11Am_DetailApiExcludesAdmins() throws Exception {
        ReflectionTestUtils.setField(dashboardService, "cutoffTimeStr", "00:00");
        lenient().when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        lenient().when(userRepository.findAll()).thenReturn(List.of(superAdmin, stdAdmin, hodUserDeptA, counselor1DeptA, counselor2DeptB));

        List<Object[]> followUpCounts = List.of(
                new Object[]{superAdmin.getId(), 2L},
                new Object[]{stdAdmin.getId(), 2L},
                new Object[]{hodUserDeptA.getId(), 2L},
                new Object[]{counselor1DeptA.getId(), 2L},
                new Object[]{counselor2DeptB.getId(), 2L}
        );
        lenient().when(leadFollowUpRepository.countScheduledFollowUpsGroupedByUserBetween(any(), any())).thenReturn(followUpCounts);
        lenient().when(leadFollowUpRepository.findEarliestScheduledFollowUpGroupedByUserBetween(any(), any())).thenReturn(new ArrayList<>());
        lenient().when(activityLogRepository.findDailyLoginStatsGroupedByPerformedBy(any(), any())).thenReturn(new ArrayList<>());

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpUserNotLoggedInPageResponseDTO response = dashboardService.getFollowUpUsersNotLoggedInBy11Am(pageRequest);

        assertNotNull(response);
        assertEquals(3, response.getTotalElements());

        List<UUID> returnedIds = response.getContent().stream().map(FollowUpUserNotLoggedInSummaryDTO::getUserId).toList();
        assertTrue(returnedIds.contains(hodUserDeptA.getId()));
        assertTrue(returnedIds.contains(counselor1DeptA.getId()));
        assertTrue(returnedIds.contains(counselor2DeptB.getId()));
        assertFalse(returnedIds.contains(superAdmin.getId()));
        assertFalse(returnedIds.contains(stdAdmin.getId()));
    }

    @Test
    @DisplayName("TEST 9: Pagination and totalElements only reflect eligible operational users")
    void testPagination_CalculatesCorrectTotalElements() throws Exception {
        lenient().when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        lenient().when(userRepository.findAll()).thenReturn(List.of(superAdmin, stdAdmin, hodUserDeptA, counselor1DeptA, counselor2DeptB));
        lenient().when(activityLogRepository.findDailyLoginStatsGroupedByPerformedBy(any(), any())).thenReturn(new ArrayList<>());

        // Request page 0 with size 2 (out of 3 eligible users)
        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(2).build();
        UserNotLoggedInPageResponseDTO response = dashboardService.getUsersNotLoggedInToday(pageRequest);

        assertNotNull(response);
        assertEquals(3, response.getTotalElements());
        assertEquals(2, response.getTotalPages());
        assertEquals(2, response.getContent().size());
        assertFalse(response.isLast());

        // Request page 1 with size 2
        PageRequestDTO pageRequest1 = PageRequestDTO.builder().page(1).size(2).build();
        UserNotLoggedInPageResponseDTO response1 = dashboardService.getUsersNotLoggedInToday(pageRequest1);
        assertEquals(1, response1.getContent().size());
        assertTrue(response1.isLast());
    }

    @Test
    @DisplayName("TEST 10: Search for 'Admin' never returns Admin or Super Admin records")
    void testSearch_NeverReturnsAdminRecords() throws Exception {
        lenient().when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        lenient().when(userRepository.findAll()).thenReturn(List.of(superAdmin, stdAdmin, hodUserDeptA, counselor1DeptA, counselor2DeptB));
        lenient().when(activityLogRepository.findDailyLoginStatsGroupedByPerformedBy(any(), any())).thenReturn(new ArrayList<>());

        // Search for "Admin"
        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).search("Admin").build();
        UserNotLoggedInPageResponseDTO response = dashboardService.getUsersNotLoggedInToday(pageRequest);

        assertNotNull(response);
        assertEquals(0, response.getTotalElements());
        assertTrue(response.getContent().isEmpty());
    }

    @Test
    @DisplayName("TEST 11: Data Scoping for HOD: HOD sees ONLY eligible operational users in their department")
    void testHodDepartmentScoping_RestrictsToDepartmentEligibleUsersOnly() throws Exception {
        lenient().when(dataScopeService.getScopeForCurrentUser()).thenReturn(hodDeptAScope);
        lenient().when(userRepository.findAll()).thenReturn(List.of(superAdmin, stdAdmin, hodUserDeptA, counselor1DeptA, counselor2DeptB));
        lenient().when(activityLogRepository.findDailyLoginStatsGroupedByPerformedBy(any(), any())).thenReturn(new ArrayList<>());

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        UserNotLoggedInPageResponseDTO response = dashboardService.getUsersNotLoggedInToday(pageRequest);

        assertNotNull(response);
        // HOD Dept A sees only hodUserDeptA and counselor1DeptA (Dept A). counselor2DeptB (Dept B), superAdmin and stdAdmin are not visible.
        assertEquals(2, response.getTotalElements());
        List<UUID> ids = response.getContent().stream().map(UserNotLoggedInSummaryDTO::getUserId).toList();
        assertTrue(ids.contains(hodUserDeptA.getId()));
        assertTrue(ids.contains(counselor1DeptA.getId()));
        assertFalse(ids.contains(counselor2DeptB.getId()));
        assertFalse(ids.contains(superAdmin.getId()));
        assertFalse(ids.contains(stdAdmin.getId()));
    }
}
