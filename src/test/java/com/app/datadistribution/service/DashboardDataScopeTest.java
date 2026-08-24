package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest;
import com.app.datadistribution.dto.dashboard.DashboardAnalyticsResponseDTO;
import com.app.datadistribution.dto.dashboard.DashboardSummaryDTO;
import com.app.datadistribution.dto.dashboard.GroupCountDTO;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.HodAccessType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.ActivityLogRepository;
import com.app.datadistribution.repository.DashboardAnalyticsRepository;
import com.app.datadistribution.repository.DashboardCardRepository;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserDashboardCardPreferenceRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.DashboardServiceImpl;
import com.app.datadistribution.service.impl.UserDataScopeServiceImpl;
import com.app.datadistribution.service.interfaces.IDashboardCardPermissionService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class DashboardDataScopeTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private DashboardCardRepository dashboardCardRepository;
    @Mock
    private UserDashboardCardPreferenceRepository userPreferenceRepository;
    @Mock
    private DashboardAnalyticsRepository dashboardAnalyticsRepository;
    @Mock
    private IDashboardCardPermissionService dashboardCardPermissionService;
    @Mock
    private IUserDataScopeService dataScopeService;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private UserDataScopeServiceImpl scopeService;

    private User adminUser;
    private User hodUser;
    private User counselorUser;
    private Department deptA;
    private Department deptB;

    @BeforeEach
    void setUp() {
        scopeService = new UserDataScopeServiceImpl(userRepository, departmentRepository);

        deptA = Department.builder().name("Dept A").active(true).build();
        deptA.setId(UUID.randomUUID());

        deptB = Department.builder().name("Dept B").active(true).build();
        deptB.setId(UUID.randomUUID());

        Role adminRole = Role.builder().name(RoleType.SUPER_ADMIN.name()).build();
        adminUser = User.builder().username("admin").active(true).roles(Set.of(adminRole)).build();
        adminUser.setId(UUID.randomUUID());

        Role hodRole = Role.builder().name("HOD_MANAGEMENT").build();
        hodUser = User.builder().username("hod_user").active(true).roles(Set.of(hodRole))
                .departments(Set.of(deptA, deptB)).hodAccessType(HodAccessType.FULL_ACCESS).build();
        hodUser.setId(UUID.randomUUID());

        Role counselorRole = Role.builder().name("COUNSELOR").build();
        counselorUser = User.builder().username("counselor_user").active(true).roles(Set.of(counselorRole))
                .departments(Set.of(deptA)).build();
        counselorUser.setId(UUID.randomUUID());

        // Setup generic JPA Criteria builder mocks for countFollowUpsTodayInScope & conversation ratio
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery cq = mock(CriteriaQuery.class);
        Root root = mock(Root.class);
        Path path = mock(Path.class);
        TypedQuery tq = mock(TypedQuery.class);
        Predicate pred = mock(Predicate.class);

        lenient().when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        lenient().when(cb.createQuery(any())).thenReturn(cq);
        lenient().when(cq.from(any(Class.class))).thenReturn(root);
        lenient().when(root.get(anyString())).thenReturn(path);
        lenient().when(path.get(anyString())).thenReturn(path);
        lenient().when(cb.equal(any(), any())).thenReturn(pred);
        lenient().when(cb.between(any(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(pred);
        lenient().when(cb.or(any(Predicate[].class))).thenReturn(pred);
        lenient().when(cb.and(any(Predicate[].class))).thenReturn(pred);
        lenient().when(cb.countDistinct(any())).thenReturn(mock(jakarta.persistence.criteria.Expression.class));
        lenient().when(cq.select(any())).thenReturn(cq);
        lenient().when(cq.where(any(Predicate[].class))).thenReturn(cq);
        lenient().when(entityManager.createQuery(cq)).thenReturn(tq);
        lenient().when(tq.getSingleResult()).thenReturn(0L);
    }

    // --- Scope Resolution & Escalation Tests ---

    @Test
    void testAdminScope_DefaultIsSystem() throws Exception {
        lenient().when(departmentRepository.findByActiveTrueAndIsDeletedFalse()).thenReturn(List.of(deptA, deptB));
        lenient().when(userRepository.findAll()).thenReturn(List.of(adminUser, hodUser, counselorUser));

        UserDataScope scope = scopeService.getScopeForUser(adminUser, (String) null);
        assertNotNull(scope);
        assertEquals(ScopeType.SYSTEM, scope.getScopeType());
        assertTrue(scope.isAdmin());
        assertFalse(scope.isSelfScopeOnly());
    }

    @Test
    void testAdminScope_CanRequestSelf() throws Exception {
        UserDataScope scope = scopeService.getScopeForUser(adminUser, "SELF");
        assertNotNull(scope);
        assertEquals(ScopeType.SELF, scope.getScopeType());
        assertEquals(adminUser.getId(), scope.getUserId());
        assertTrue(scope.isSelfScopeOnly());
    }

    @Test
    void testHodScope_DefaultIsDepartment_AggregatesMultiDepartments() throws Exception {
        lenient().when(userRepository.findAll()).thenReturn(List.of(hodUser, counselorUser));

        UserDataScope scope = scopeService.getScopeForUser(hodUser, (String) null);
        assertNotNull(scope);
        assertEquals(ScopeType.DEPARTMENT, scope.getScopeType());
        assertTrue(scope.isHod());
        assertFalse(scope.isAdmin());
        assertTrue(scope.getDepartmentIds().contains(deptA.getId()));
        assertTrue(scope.getDepartmentIds().contains(deptB.getId()));
        assertTrue(scope.getDepartmentUserIds().contains(counselorUser.getId()));
        assertTrue(scope.getDepartmentUserIds().contains(hodUser.getId()));
    }

    @Test
    void testHodScope_CanRequestSelf() throws Exception {
        UserDataScope scope = scopeService.getScopeForUser(hodUser, "SELF");
        assertNotNull(scope);
        assertEquals(ScopeType.SELF, scope.getScopeType());
        assertEquals(hodUser.getId(), scope.getUserId());
        assertTrue(scope.isSelfScopeOnly());
    }

    @Test
    void testHodScope_RequestingSystem_ThrowsUnauthorizedException() {
        assertThrows(UnauthorizedException.class, () -> scopeService.getScopeForUser(hodUser, "SYSTEM"));
    }

    @Test
    void testCounselorScope_DefaultIsSelf() throws Exception {
        UserDataScope scope = scopeService.getScopeForUser(counselorUser, (String) null);
        assertNotNull(scope);
        assertEquals(ScopeType.SELF, scope.getScopeType());
        assertTrue(scope.isCounsellor());
        assertFalse(scope.isAdmin());
        assertFalse(scope.isHod());
        assertEquals(counselorUser.getId(), scope.getUserId());
    }

    @Test
    void testCounselorScope_RequestingDepartment_ThrowsUnauthorizedException() {
        assertThrows(UnauthorizedException.class, () -> scopeService.getScopeForUser(counselorUser, "DEPARTMENT"));
    }

    @Test
    void testCounselorScope_RequestingSystem_ThrowsUnauthorizedException() {
        assertThrows(UnauthorizedException.class, () -> scopeService.getScopeForUser(counselorUser, "SYSTEM"));
    }

    @Test
    void testInvalidScopeString_ThrowsBadRequestException() {
        assertThrows(BadRequestException.class, () -> scopeService.getScopeForUser(adminUser, "INVALID_SCOPE"));
    }

    // --- Dashboard Summary Dynamic Data Scope Tests ---

    @Test
    void testGetDashboardSummary_UsesResolvedScope() throws Exception {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("counselor_user");
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(userRepository.findByUsername("counselor_user")).thenReturn(Optional.of(counselorUser));

        UserDataScope counselorScope = UserDataScope.builder()
                .scopeType(ScopeType.SELF)
                .userId(counselorUser.getId())
                .currentUser(counselorUser)
                .isCounsellor(true)
                .build();

        DashboardAnalyticsFilterRequest filter = new DashboardAnalyticsFilterRequest();
        lenient().when(dataScopeService.getScopeForCurrentUser(filter)).thenReturn(counselorScope);
        lenient().when(dashboardAnalyticsRepository.fetchTotalMatchingLeads(eq(counselorScope), any())).thenReturn(42L);
        lenient().when(dashboardCardRepository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(Collections.emptyList());

        DashboardSummaryDTO summary = dashboardService.getDashboardSummary(filter);

        assertNotNull(summary);
        assertEquals("SELF", summary.getScope());
        assertEquals(42L, summary.getTotalLeads());
    }

    @Test
    void testGetLeadStatusBreakdown_DelegatesToRepositoryWithScope() throws Exception {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("hod_user");
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(userRepository.findByUsername("hod_user")).thenReturn(Optional.of(hodUser));

        DashboardAnalyticsFilterRequest filter = new DashboardAnalyticsFilterRequest();

        DashboardAnalyticsResponseDTO resp = DashboardAnalyticsResponseDTO.builder()
                .total(15L)
                .data(List.of(GroupCountDTO.builder().id(UUID.randomUUID()).name("RAW").count(15L).build()))
                .build();
        lenient().when(dashboardAnalyticsRepository.fetchAnalytics(eq(hodUser), eq(filter))).thenReturn(resp);

        List<GroupCountDTO> result = dashboardService.getLeadStatusBreakdown(filter);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("RAW", result.get(0).getName());
        assertEquals(15L, result.get(0).getCount());
    }
}
