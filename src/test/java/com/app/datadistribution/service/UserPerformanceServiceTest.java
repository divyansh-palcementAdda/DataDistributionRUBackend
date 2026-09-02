package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.datadistribution.dto.user.UserPerformanceFilterRequest;
import com.app.datadistribution.dto.user.UserPerformancePageResponse;
import com.app.datadistribution.dto.user.UserPerformanceResponse;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.UserPerformanceServiceImpl;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
class UserPerformanceServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LeadStatusRepository leadStatusRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private IUserDataScopeService dataScopeService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private UserPerformanceServiceImpl userPerformanceService;

    private User adminUser;
    private User hodA;
    private User counselorA1;
    private User counselorA2;
    private User hodB;
    private User counselorB1;

    private Department deptA;
    private Department deptB;

    private Role adminRole;
    private Role hodRole;
    private Role counselorRole;

    private UserDataScope adminScope;
    private UserDataScope hodAScope;
    private UserDataScope counselorA1Scope;

    private LeadStatus rawStatus;
    private LeadStatus registeredStatus;
    private LeadStatus connectedStatus;

    @BeforeEach
    void setUp() {
        adminRole = Role.builder().name(RoleType.ADMIN.name()).build();
        adminRole.setId(UUID.randomUUID());

        hodRole = Role.builder().name(RoleType.HOD.name()).build();
        hodRole.setId(UUID.randomUUID());

        counselorRole = Role.builder().name(RoleType.COUNSELOR.name()).build();
        counselorRole.setId(UUID.randomUUID());

        deptA = Department.builder().name("Department A").code("DEPT_A").active(true).build();
        deptA.setId(UUID.randomUUID());

        deptB = Department.builder().name("Department B").code("DEPT_B").active(true).build();
        deptB.setId(UUID.randomUUID());

        adminUser = User.builder().username("admin").firstName("System").lastName("Admin").active(true).roles(Set.of(adminRole)).build();
        adminUser.setId(UUID.randomUUID());

        hodA = User.builder().username("hodA").firstName("HOD").lastName("Alpha").active(true).roles(Set.of(hodRole)).departments(Set.of(deptA)).build();
        hodA.setId(UUID.randomUUID());

        counselorA1 = User.builder().username("counselorA1").firstName("Counselor").lastName("One").active(true).roles(Set.of(counselorRole)).departments(Set.of(deptA)).build();
        counselorA1.setId(UUID.randomUUID());

        counselorA2 = User.builder().username("counselorA2").firstName("Counselor").lastName("Two").active(true).roles(Set.of(counselorRole)).departments(Set.of(deptA)).build();
        counselorA2.setId(UUID.randomUUID());

        hodB = User.builder().username("hodB").firstName("HOD").lastName("Beta").active(true).roles(Set.of(hodRole)).departments(Set.of(deptB)).build();
        hodB.setId(UUID.randomUUID());

        counselorB1 = User.builder().username("counselorB1").firstName("Counselor").lastName("Three").active(true).roles(Set.of(counselorRole)).departments(Set.of(deptB)).build();
        counselorB1.setId(UUID.randomUUID());

        adminScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .userId(adminUser.getId())
                .currentUser(adminUser)
                .build();

        hodAScope = UserDataScope.builder()
                .scopeType(ScopeType.DEPARTMENT)
                .isAdmin(false)
                .isHod(true)
                .userId(hodA.getId())
                .currentUser(hodA)
                .departmentIds(Set.of(deptA.getId()))
                .departmentUserIds(Set.of(hodA.getId(), counselorA1.getId(), counselorA2.getId()))
                .build();

        counselorA1Scope = UserDataScope.builder()
                .scopeType(ScopeType.SELF)
                .isAdmin(false)
                .isHod(false)
                .userId(counselorA1.getId())
                .currentUser(counselorA1)
                .departmentIds(Set.of(deptA.getId()))
                .departmentUserIds(Set.of(counselorA1.getId()))
                .build();

        rawStatus = LeadStatus.builder().name("Raw").code("RAW").active(true).build();
        rawStatus.setId(UUID.randomUUID());

        registeredStatus = LeadStatus.builder().name("Registered").code("REGISTERED").active(true).build();
        registeredStatus.setId(UUID.randomUUID());

        connectedStatus = LeadStatus.builder().name("Connected").code("CONNECTED").active(true).build();
        connectedStatus.setId(UUID.randomUUID());
    }

    private void mockEmptyNativeQueries() {
        Query mockQuery = mock(Query.class);
        when(mockQuery.getResultList()).thenReturn(Collections.emptyList());
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
    }

    @Test
    @DisplayName("TEST 1 (Admin Scope): Admin sees all operational users across departments; Admins are excluded from rows")
    void testAdminScope_SeesAllOperationalUsersExcludesAdmin() throws Exception {
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(adminScope);
        when(userRepository.findAll()).thenReturn(List.of(adminUser, hodA, counselorA1, counselorA2, hodB, counselorB1));
        when(leadStatusRepository.findAll()).thenReturn(List.of(rawStatus, registeredStatus, connectedStatus));
        mockEmptyNativeQueries();

        UserPerformanceFilterRequest filter = UserPerformanceFilterRequest.builder().page(0).size(10).build();
        UserPerformancePageResponse response = userPerformanceService.getUserPerformance(filter);

        assertNotNull(response);
        assertEquals(5, response.getTotalElements(), "Must exclude adminUser and return exactly 5 operational users");
        List<UUID> returnedIds = response.getContent().stream().map(UserPerformanceResponse::getUserId).toList();
        assertFalse(returnedIds.contains(adminUser.getId()), "Admin must not be an operational row");
        assertTrue(returnedIds.contains(hodA.getId()));
        assertTrue(returnedIds.contains(counselorA1.getId()));
        assertTrue(returnedIds.contains(counselorA2.getId()));
        assertTrue(returnedIds.contains(hodB.getId()));
        assertTrue(returnedIds.contains(counselorB1.getId()));
    }

    @Test
    @DisplayName("TEST 2 (HOD Scope): HOD sees self + department operational users only; cannot see Dept B")
    void testHodScope_SeesDepartmentUsersOnly() throws Exception {
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(hodAScope);
        when(userRepository.findAll()).thenReturn(List.of(adminUser, hodA, counselorA1, counselorA2, hodB, counselorB1));
        when(leadStatusRepository.findAll()).thenReturn(List.of(rawStatus, registeredStatus, connectedStatus));
        mockEmptyNativeQueries();

        UserPerformanceFilterRequest filter = UserPerformanceFilterRequest.builder().page(0).size(10).build();
        UserPerformancePageResponse response = userPerformanceService.getUserPerformance(filter);

        assertNotNull(response);
        assertEquals(3, response.getTotalElements(), "HOD A must see 3 users (hodA, counselorA1, counselorA2)");
        List<UUID> returnedIds = response.getContent().stream().map(UserPerformanceResponse::getUserId).toList();
        assertTrue(returnedIds.contains(hodA.getId()));
        assertTrue(returnedIds.contains(counselorA1.getId()));
        assertTrue(returnedIds.contains(counselorA2.getId()));
        assertFalse(returnedIds.contains(hodB.getId()));
        assertFalse(returnedIds.contains(counselorB1.getId()));
    }

    @Test
    @DisplayName("TEST 3 (HOD Scope): Department filter bypass attempt for Dept B returns empty")
    void testHodScope_DepartmentFilterBypassReturnsEmpty() throws Exception {
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(hodAScope);
        when(userRepository.findAll()).thenReturn(List.of(adminUser, hodA, counselorA1, counselorA2, hodB, counselorB1));

        UserPerformanceFilterRequest filter = UserPerformanceFilterRequest.builder()
                .departmentId(deptB.getId()) // HOD A tries to filter for Dept B
                .page(0).size(10).build();

        UserPerformancePageResponse response = userPerformanceService.getUserPerformance(filter);

        assertNotNull(response);
        assertEquals(0, response.getTotalElements(), "HOD A cannot access Dept B data");
        assertTrue(response.getContent().isEmpty());
    }

    @Test
    @DisplayName("TEST 4 (HOD Scope): Search bypass attempt for counselor in Dept B returns empty")
    void testHodScope_SearchBypassReturnsEmpty() throws Exception {
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(hodAScope);
        when(userRepository.findAll()).thenReturn(List.of(adminUser, hodA, counselorA1, counselorA2, hodB, counselorB1));

        UserPerformanceFilterRequest filter = UserPerformanceFilterRequest.builder()
                .search("Three") // counselorB1's last name
                .page(0).size(10).build();

        UserPerformancePageResponse response = userPerformanceService.getUserPerformance(filter);

        assertNotNull(response);
        assertEquals(0, response.getTotalElements(), "Search must not bypass HOD departmental authorization");
    }

    @Test
    @DisplayName("TEST 5 (Counselor Scope): Counselor sees self only (1 row)")
    void testCounselorScope_SeesSelfOnly() throws Exception {
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(counselorA1Scope);
        when(leadStatusRepository.findAll()).thenReturn(List.of(rawStatus, registeredStatus, connectedStatus));
        mockEmptyNativeQueries();

        UserPerformanceFilterRequest filter = UserPerformanceFilterRequest.builder().page(0).size(10).build();
        UserPerformancePageResponse response = userPerformanceService.getUserPerformance(filter);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(counselorA1.getId(), response.getContent().get(0).getUserId());
    }

    @Test
    @DisplayName("TEST 6 (Counselor Scope): Search for another counselor returns empty")
    void testCounselorScope_SearchAnotherUserReturnsEmpty() throws Exception {
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(counselorA1Scope);

        UserPerformanceFilterRequest filter = UserPerformanceFilterRequest.builder()
                .search("Two") // counselorA2's last name
                .page(0).size(10).build();

        UserPerformancePageResponse response = userPerformanceService.getUserPerformance(filter);

        assertNotNull(response);
        assertEquals(0, response.getTotalElements());
    }

    @Test
    @DisplayName("TEST 7 (Individual Metric Isolation): Each user row contains their specific metrics")
    void testIndividualMetricIsolation_EachRowHasOwnMetrics() throws Exception {
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(hodAScope);
        when(userRepository.findAll()).thenReturn(List.of(hodA, counselorA1));
        when(leadStatusRepository.findAll()).thenReturn(List.of(rawStatus, registeredStatus, connectedStatus));

        // Mock lead stats: hodA has 10 allotted / 5 availed / 2 raw / 1 registered / 3 connected
        // counselorA1 has 50 allotted / 20 availed / 15 raw / 8 registered / 12 connected
        Object[] leadRowHod = new Object[] { hodA.getId(), 10L, 5L, 2L, 1L, 3L };
        Object[] leadRowC1 = new Object[] { counselorA1.getId(), 50L, 20L, 15L, 8L, 12L };

        Query leadQuery = mock(Query.class);
        when(leadQuery.getResultList()).thenReturn(Arrays.asList(leadRowHod, leadRowC1));

        // Mock followup stats
        Object[] fuRowHod = new Object[] { hodA.getId(), 4L, 4L, 1L, 2L, 1L };
        Object[] fuRowC1 = new Object[] { counselorA1.getId(), 15L, 15L, 3L, 5L, 7L };

        Query fuQuery = mock(Query.class);
        when(fuQuery.getResultList()).thenReturn(Arrays.asList(fuRowHod, fuRowC1));

        // Mock session stats
        LocalDateTime now = LocalDateTime.now();
        Object[] sessRowC1 = new Object[] { UUID.randomUUID(), counselorA1.getId(), now.minusHours(4), null, now.minusMinutes(5), "ACTIVE", 0L };

        Query sessQuery = mock(Query.class);
        when(sessQuery.getResultList()).thenReturn(Collections.singletonList(sessRowC1));

        Query periodQuery = mock(Query.class);
        when(periodQuery.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(leadQuery)
                .thenReturn(fuQuery)
                .thenReturn(sessQuery)
                .thenReturn(periodQuery);

        UserPerformanceFilterRequest filter = UserPerformanceFilterRequest.builder().sortBy("userName").sortDirection("ASC").build();
        UserPerformancePageResponse response = userPerformanceService.getUserPerformance(filter);

        assertNotNull(response);
        assertEquals(2, response.getContent().size());

        UserPerformanceResponse c1Dto = response.getContent().stream()
                .filter(u -> u.getUserId().equals(counselorA1.getId())).findFirst().orElseThrow();
        assertEquals(50L, c1Dto.getTotalAllottedData());
        assertEquals(20L, c1Dto.getTotalAvailedData());
        assertEquals(15L, c1Dto.getRawDataCount());
        assertEquals(8L, c1Dto.getRegisteredDataCount());
        assertEquals(12L, c1Dto.getTodayConnectedCalls());
        assertEquals(15L, c1Dto.getTodayFollowupsCount());
        assertEquals(15L, c1Dto.getTodayFollowupsScheduled());
        assertEquals(3L, c1Dto.getTodayMissedFollowups());
        assertEquals(5L, c1Dto.getTodayUpcomingFollowups());
        assertEquals(7L, c1Dto.getTodayPendingFollowups());
        assertTrue(c1Dto.getCurrentlyWorking(), "Last activity 5 min ago with ACTIVE status must be currently working");

        UserPerformanceResponse hodDto = response.getContent().stream()
                .filter(u -> u.getUserId().equals(hodA.getId())).findFirst().orElseThrow();
        assertEquals(10L, hodDto.getTotalAllottedData(), "HOD row must contain only HOD's own metrics, NOT department total");
        assertEquals(5L, hodDto.getTotalAvailedData());
        assertEquals(4L, hodDto.getTodayFollowupsCount());
        assertFalse(hodDto.getCurrentlyWorking());
    }
}
