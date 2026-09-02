package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadSourcePageResponse;
import com.app.datadistribution.dto.lead.LeadSourceResponse;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.LeadSourceRepositoryCustomImpl;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.LeadSourceServiceImpl;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
class LeadSourceLeadStatsTest {

    @Mock
    private LeadSourceRepository leadSourceRepository;

    @Mock
    private com.app.datadistribution.mapper.LeadMapper leadMapper;

    @Mock
    private com.app.datadistribution.service.interfaces.IDashboardCardPermissionService dashboardCardPermissionService;

    @Mock
    private IUserDataScopeService dataScopeService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private LeadSourceServiceImpl leadSourceService;

    private LeadSourceRepositoryCustomImpl repositoryCustomImpl;

    private UserDataScope systemScope;
    private UserDataScope hodScope;
    private UserDataScope counselorScope;

    private UUID adminUserId;
    private UUID hodUserId;
    private UUID counselorUserId;
    private UUID deptId;

    @BeforeEach
    void setUp() {
        repositoryCustomImpl = new LeadSourceRepositoryCustomImpl(entityManager);

        adminUserId = UUID.randomUUID();
        hodUserId = UUID.randomUUID();
        counselorUserId = UUID.randomUUID();
        deptId = UUID.randomUUID();

        systemScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .userId(adminUserId)
                .build();

        hodScope = UserDataScope.builder()
                .scopeType(ScopeType.DEPARTMENT)
                .isAdmin(false)
                .isHod(true)
                .userId(hodUserId)
                .departmentIds(Set.of(deptId))
                .departmentUserIds(Set.of(hodUserId, counselorUserId))
                .build();

        counselorScope = UserDataScope.builder()
                .scopeType(ScopeType.SELF)
                .isAdmin(false)
                .isHod(false)
                .userId(counselorUserId)
                .build();
    }

    @Test
    @DisplayName("A. Basic response: lead source contains all 3 calculated metrics")
    void testBasicResponse_ContainsAllThreeMetrics() throws Exception {
        UUID lsId = UUID.randomUUID();
        LeadSourceResponse lsDto = LeadSourceResponse.builder()
                .id(lsId)
                .name("Facebook")
                .code("FB")
                .description("Facebook Ads")
                .active(true)
                .status("ACTIVE")
                .totalData(100L)
                .totalAllottedData(70L)
                .totalAvailedData(40L)
                .build();

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).sortBy("name").sortDirection("ASC").build();
        LeadSourcePageResponse mockPage = LeadSourcePageResponse.builder()
                .content(List.of(lsDto))
                .page(0)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .last(true)
                .build();

        when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        when(leadSourceRepository.fetchLeadSourcesWithLeadStats(request, null, systemScope)).thenReturn(mockPage);

        LeadSourcePageResponse response = leadSourceService.getAll(request, null);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        LeadSourceResponse result = response.getContent().get(0);
        assertEquals("Facebook", result.getName());
        assertEquals(100L, result.getTotalData());
        assertEquals(70L, result.getTotalAllottedData());
        assertEquals(40L, result.getTotalAvailedData());
    }

    @Test
    @DisplayName("B. Correct aggregation: repository mapping maps SQL columns accurately")
    void testRepositoryMapping_MapsAggregatedRows() {
        UUID lsId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // Object array matching the SQL select:
        // ls.id, ls.name, ls.code, ls.description, ls.active, ls.created_at, ls.updated_at,
        // total_data, total_allotted_data, total_availed_data
        Object[] row1 = new Object[] {
                lsId, "Facebook", "FB", "Facebook Ads", true, now, now,
                100L, 70L, 40L
        };

        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.singletonList(row1));

        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(countQuery)
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).sortBy("totalData").sortDirection("DESC").build();
        LeadSourcePageResponse response = repositoryCustomImpl.fetchLeadSourcesWithLeadStats(request, null, systemScope);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());

        LeadSourceResponse ls = response.getContent().get(0);
        assertEquals(lsId, ls.getId());
        assertEquals("Facebook", ls.getName());
        assertEquals("FB", ls.getCode());
        assertEquals("Facebook Ads", ls.getDescription());
        assertTrue(ls.isActive());
        assertEquals("ACTIVE", ls.getStatus());
        assertEquals(100L, ls.getTotalData());
        assertEquals(70L, ls.getTotalAllottedData());
        assertEquals(40L, ls.getTotalAvailedData());
    }

    @Test
    @DisplayName("C. Zero values: lead source with no leads returns 0, 0, 0 (not null)")
    void testZeroValues_ReturnsZeros() {
        UUID lsId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Object[] rowEmpty = new Object[] {
                lsId, "Website", "WEB", "Organic Web", true, now, now,
                0L, 0L, 0L
        };

        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.singletonList(rowEmpty));

        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(countQuery)
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).build();
        LeadSourcePageResponse response = repositoryCustomImpl.fetchLeadSourcesWithLeadStats(request, null, systemScope);

        assertNotNull(response);
        LeadSourceResponse ls = response.getContent().get(0);
        assertEquals(0L, ls.getTotalData());
        assertEquals(0L, ls.getTotalAllottedData());
        assertEquals(0L, ls.getTotalAvailedData());
    }

    @Test
    @DisplayName("D & E. Sorting & Pagination: verifies SQL generation includes ORDER BY and LIMIT/OFFSET")
    void testSortingAndPagination_GeneratesCorrectSQL() {
        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(20L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createNativeQuery(contains("SELECT COUNT")))
                .thenReturn(countQuery);
        when(entityManager.createNativeQuery(contains("SELECT ls.id")))
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .sortBy("totalAllottedData")
                .sortDirection("DESC")
                .build();

        LeadSourcePageResponse response = repositoryCustomImpl.fetchLeadSourcesWithLeadStats(request, null, systemScope);

        assertNotNull(response);
        assertEquals(20L, response.getTotalElements());
        assertEquals(2, response.getTotalPages());
        assertEquals(1, response.getPage());
        assertTrue(response.isLast());

        verify(dataQuery).setParameter("limit", 10);
        verify(dataQuery).setParameter("offset", 10);
    }

    @Test
    @DisplayName("F. Search & Status Filter: parameters bound to queries")
    void testSearchAndStatus_BindsParameters() {
        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createNativeQuery(contains("SELECT COUNT")))
                .thenReturn(countQuery);
        when(entityManager.createNativeQuery(contains("SELECT ls.id")))
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder()
                .page(0)
                .size(10)
                .search("Face")
                .sortBy("totalAvailedData")
                .sortDirection("ASC")
                .build();

        repositoryCustomImpl.fetchLeadSourcesWithLeadStats(request, "ACTIVE", systemScope);

        verify(countQuery).setParameter("searchPattern", "%face%");
        verify(countQuery).setParameter("activeStatus", true);
        verify(dataQuery).setParameter("searchPattern", "%face%");
        verify(dataQuery).setParameter("activeStatus", true);
    }

    @Test
    @DisplayName("H. Data Scoping: HOD and SELF scopes bind user and department parameters")
    void testDataScoping_BindsDepartmentAndUserParams() {
        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createNativeQuery(contains("SELECT COUNT")))
                .thenReturn(countQuery);
        when(entityManager.createNativeQuery(contains("SELECT ls.id")))
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).build();

        // 1. Test HOD scope
        repositoryCustomImpl.fetchLeadSourcesWithLeadStats(request, null, hodScope);
        verify(dataQuery, atLeastOnce()).setParameter("scopeUserId", hodUserId);
        verify(dataQuery, atLeastOnce()).setParameter("scopeDeptIds", Set.of(deptId));

        // 2. Test Counselor scope
        reset(dataQuery, countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        repositoryCustomImpl.fetchLeadSourcesWithLeadStats(request, null, counselorScope);
        verify(dataQuery, atLeastOnce()).setParameter("scopeUserId", counselorUserId);
    }

    @Test
    @DisplayName("I. Lead Source Visibility: Non-admin scopes generate INNER JOIN with stats.total_data > 0")
    void testLeadSourceVisibility_NonAdminUsesInnerJoin() {
        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createNativeQuery(contains("INNER JOIN")))
                .thenReturn(countQuery)
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).build();
        repositoryCustomImpl.fetchLeadSourcesWithLeadStats(request, null, hodScope);

        verify(entityManager, times(1)).createNativeQuery(contains("SELECT COUNT(ls.id) FROM lead_sources ls INNER JOIN"));
        verify(entityManager, times(1)).createNativeQuery(contains("SELECT ls.id AS id, ls.name AS name, ls.code AS code, ls.description AS description, ls.active AS active, ls.created_at AS created_at, ls.updated_at AS updated_at, COALESCE(stats.total_data, 0) AS total_data, COALESCE(stats.total_allotted_data, 0) AS total_allotted_data, COALESCE(stats.total_availed_data, 0) AS total_availed_data FROM lead_sources ls INNER JOIN"));
    }
}
