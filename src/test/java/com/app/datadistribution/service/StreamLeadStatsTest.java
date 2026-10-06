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
import com.app.datadistribution.dto.stream.StreamPageResponse;
import com.app.datadistribution.dto.stream.StreamResponse;
import com.app.datadistribution.repository.StreamRepository;
import com.app.datadistribution.repository.StreamRepositoryCustomImpl;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.StreamServiceImpl;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
class StreamLeadStatsTest {

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private com.app.datadistribution.mapper.LeadMapper leadMapper;

    @Mock
    private com.app.datadistribution.service.interfaces.IDashboardCardPermissionService dashboardCardPermissionService;

    @Mock
    private IUserDataScopeService dataScopeService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private StreamServiceImpl streamService;

    private StreamRepositoryCustomImpl repositoryCustomImpl;

    private UserDataScope systemScope;
    private UserDataScope hodScope;
    private UserDataScope counselorScope;

    private UUID adminUserId;
    private UUID hodUserId;
    private UUID counselorUserId;
    private UUID deptId;

    @BeforeEach
    void setUp() {
        repositoryCustomImpl = new StreamRepositoryCustomImpl(entityManager);

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
    @DisplayName("A. Basic response: stream contains all 4 calculated metrics")
    void testBasicResponse_ContainsAllFourMetrics() throws Exception {
        UUID streamId = UUID.randomUUID();
        StreamResponse streamDto = StreamResponse.builder()
                .id(streamId)
                .name("Commerce")
                .code("COMMERCE")
                .description("Commerce Stream")
                .active(true)
                .status("ACTIVE")
                .displayOrder(1)
                .totalData(1500L)
                .totalAllottedData(1000L)
                .totalUnallottedData(500L)
                .totalAvailedData(650L)
                .build();

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).sortBy("name").sortDirection("ASC").build();
        StreamPageResponse mockPage = StreamPageResponse.builder()
                .content(List.of(streamDto))
                .pageNumber(0)
                .pageSize(10)
                .totalElements(1L)
                .totalPages(1)
                .last(true)
                .build();

        when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        when(streamRepository.fetchStreamsWithLeadStats(request, null, systemScope)).thenReturn(mockPage);

        StreamPageResponse response = streamService.getAll(request, null);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        StreamResponse result = response.getContent().get(0);
        assertEquals("Commerce", result.getName());
        assertEquals(1500L, result.getTotalData());
        assertEquals(1000L, result.getTotalAllottedData());
        assertEquals(500L, result.getTotalUnallottedData());
        assertEquals(650L, result.getTotalAvailedData());
    }

    @Test
    @DisplayName("B. Correct aggregation: repository mapping maps SQL columns accurately")
    void testRepositoryMapping_MapsAggregatedRows() {
        UUID streamId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // Object array matching the SQL select:
        // s.id, s.name, s.code, s.description, s.active, s.display_order, s.created_at, s.updated_at,
        // total_data, total_allotted_data, total_unallotted_data, total_availed_data
        Object[] row1 = new Object[] {
                streamId, "Commerce", "COM001", "Commerce Stream", true, 1, now, now,
                1500L, 1000L, 500L, 650L
        };

        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.singletonList(row1));

        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(countQuery)
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).sortBy("totalData").sortDirection("DESC").build();
        StreamPageResponse response = repositoryCustomImpl.fetchStreamsWithLeadStats(request, null, systemScope);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());

        StreamResponse stream = response.getContent().get(0);
        assertEquals(streamId, stream.getId());
        assertEquals("Commerce", stream.getName());
        assertEquals("COM001", stream.getCode());
        assertEquals("Commerce Stream", stream.getDescription());
        assertTrue(stream.isActive());
        assertEquals("ACTIVE", stream.getStatus());
        assertEquals(1, stream.getDisplayOrder());
        assertEquals(1500L, stream.getTotalData());
        assertEquals(1000L, stream.getTotalAllottedData());
        assertEquals(500L, stream.getTotalUnallottedData());
        assertEquals(650L, stream.getTotalAvailedData());
    }

    @Test
    @DisplayName("C. Zero values: stream with no leads returns 0, 0, 0, 0 (not null)")
    void testZeroValues_ReturnsZeros() {
        UUID streamId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Object[] rowEmpty = new Object[] {
                streamId, "Arts", "ARTS", "Arts Stream", true, 2, now, now,
                0L, 0L, 0L, 0L
        };

        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.singletonList(rowEmpty));

        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(countQuery)
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).build();
        StreamPageResponse response = repositoryCustomImpl.fetchStreamsWithLeadStats(request, null, systemScope);

        assertNotNull(response);
        StreamResponse stream = response.getContent().get(0);
        assertEquals(0L, stream.getTotalData());
        assertEquals(0L, stream.getTotalAllottedData());
        assertEquals(0L, stream.getTotalUnallottedData());
        assertEquals(0L, stream.getTotalAvailedData());
    }

    @Test
    @DisplayName("D. System scope: native SQL contains 1=1 without user/department restriction")
    void testDataScope_SystemScope_QueryContainsNoDepartmentOrUserRestrictions() {
        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createNativeQuery(contains("SELECT COUNT")))
                .thenReturn(countQuery);
        when(entityManager.createNativeQuery(contains("SELECT s.id")))
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).build();
        repositoryCustomImpl.fetchStreamsWithLeadStats(request, null, systemScope);

        verify(entityManager, atLeastOnce()).createNativeQuery(argThat((String sql) ->
                sql.contains("1=1") && !sql.contains("scopeDeptIds") && !sql.contains("scopeUserId")
        ));
    }

    @Test
    @DisplayName("E. Department scope: native SQL includes department/user parameters")
    void testDataScope_DepartmentScope_QueryContainsDepartmentOrUserRestrictions() {
        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createNativeQuery(contains("SELECT COUNT")))
                .thenReturn(countQuery);
        when(entityManager.createNativeQuery(contains("SELECT s.id")))
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).build();
        repositoryCustomImpl.fetchStreamsWithLeadStats(request, null, hodScope);

        verify(entityManager, atLeastOnce()).createNativeQuery(argThat((String sql) ->
                sql.contains("scopeDeptIds") || sql.contains("scopeDeptUserIds") || sql.contains("scopeUserId")
        ));
    }

    @Test
    @DisplayName("F. Self scope: native SQL enforces l.assigned_to_id = :scopeUserId")
    void testDataScope_SelfScope_QueryContainsAssignedToRestriction() {
        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createNativeQuery(contains("SELECT COUNT")))
                .thenReturn(countQuery);
        when(entityManager.createNativeQuery(contains("SELECT s.id")))
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).build();
        repositoryCustomImpl.fetchStreamsWithLeadStats(request, null, counselorScope);

        verify(entityManager, atLeastOnce()).createNativeQuery(argThat((String sql) ->
                sql.contains("l.assigned_to_id = :scopeUserId")
        ));
    }

    @Test
    @DisplayName("G. Sorting: allows sorting by all calculated metric fields")
    void testSortingByMetricFields() {
        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createNativeQuery(contains("SELECT COUNT")))
                .thenReturn(countQuery);
        when(entityManager.createNativeQuery(contains("SELECT s.id")))
                .thenReturn(dataQuery);

        String[] sortFields = {"totalData", "totalAllottedData", "totalUnallottedData", "totalAvailedData"};
        for (String sf : sortFields) {
            PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).sortBy(sf).sortDirection("DESC").build();
            assertDoesNotThrow(() -> repositoryCustomImpl.fetchStreamsWithLeadStats(request, null, systemScope));
        }
    }
}
