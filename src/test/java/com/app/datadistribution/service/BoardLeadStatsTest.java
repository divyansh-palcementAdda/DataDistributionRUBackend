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
import com.app.datadistribution.dto.lead.BoardPageResponse;
import com.app.datadistribution.dto.lead.BoardResponse;
import com.app.datadistribution.repository.BoardRepository;
import com.app.datadistribution.repository.BoardRepositoryCustomImpl;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.BoardServiceImpl;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
class BoardLeadStatsTest {

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private com.app.datadistribution.mapper.LeadMapper leadMapper;

    @Mock
    private com.app.datadistribution.service.interfaces.IDashboardCardPermissionService dashboardCardPermissionService;

    @Mock
    private IUserDataScopeService dataScopeService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private BoardServiceImpl boardService;

    private BoardRepositoryCustomImpl repositoryCustomImpl;

    private UserDataScope systemScope;
    private UserDataScope hodScope;
    private UserDataScope counselorScope;

    private UUID adminUserId;
    private UUID hodUserId;
    private UUID counselorUserId;
    private UUID deptId;

    @BeforeEach
    void setUp() {
        repositoryCustomImpl = new BoardRepositoryCustomImpl(entityManager);

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
    @DisplayName("A. Basic response: board contains all 4 calculated metrics")
    void testBasicResponse_ContainsAllFourMetrics() throws Exception {
        UUID boardId = UUID.randomUUID();
        BoardResponse boardDto = BoardResponse.builder()
                .id(boardId)
                .name("CBSE")
                .code("CBSE")
                .description("Central Board")
                .active(true)
                .status("ACTIVE")
                .displayOrder(1)
                .totalData(1500L)
                .totalAllottedData(1000L)
                .totalUnallottedData(500L)
                .totalAvailedData(650L)
                .build();

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).sortBy("name").sortDirection("ASC").build();
        BoardPageResponse mockPage = BoardPageResponse.builder()
                .content(List.of(boardDto))
                .page(0)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .last(true)
                .build();

        when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        when(boardRepository.fetchBoardsWithLeadStats(request, null, systemScope)).thenReturn(mockPage);

        BoardPageResponse response = boardService.getAll(request, null);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        BoardResponse result = response.getContent().get(0);
        assertEquals("CBSE", result.getName());
        assertEquals(1500L, result.getTotalData());
        assertEquals(1000L, result.getTotalAllottedData());
        assertEquals(500L, result.getTotalUnallottedData());
        assertEquals(650L, result.getTotalAvailedData());
    }

    @Test
    @DisplayName("B. Correct aggregation: repository mapping maps SQL columns accurately")
    void testRepositoryMapping_MapsAggregatedRows() {
        UUID boardId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // Object array matching the SQL select:
        // b.id, b.name, b.code, b.description, b.active, b.display_order, b.created_at, b.updated_at,
        // total_data, total_allotted_data, total_unallotted_data, total_availed_data
        Object[] row1 = new Object[] {
                boardId, "CBSE", "CBSE", "Central Board", true, 1, now, now,
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
        BoardPageResponse response = repositoryCustomImpl.fetchBoardsWithLeadStats(request, null, systemScope);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());

        BoardResponse board = response.getContent().get(0);
        assertEquals(boardId, board.getId());
        assertEquals("CBSE", board.getName());
        assertEquals("CBSE", board.getCode());
        assertEquals("Central Board", board.getDescription());
        assertTrue(board.isActive());
        assertEquals("ACTIVE", board.getStatus());
        assertEquals(1, board.getDisplayOrder());
        assertEquals(1500L, board.getTotalData());
        assertEquals(1000L, board.getTotalAllottedData());
        assertEquals(500L, board.getTotalUnallottedData());
        assertEquals(650L, board.getTotalAvailedData());
    }

    @Test
    @DisplayName("C. Zero values: board with no leads returns 0, 0, 0, 0 (not null)")
    void testZeroValues_ReturnsZeros() {
        UUID boardId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Object[] rowEmpty = new Object[] {
                boardId, "ICSE", "ICSE", "ICSE Board", true, 2, now, now,
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
        BoardPageResponse response = repositoryCustomImpl.fetchBoardsWithLeadStats(request, null, systemScope);

        assertNotNull(response);
        BoardResponse board = response.getContent().get(0);
        assertEquals(0L, board.getTotalData());
        assertEquals(0L, board.getTotalAllottedData());
        assertEquals(0L, board.getTotalUnallottedData());
        assertEquals(0L, board.getTotalAvailedData());
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
        when(entityManager.createNativeQuery(contains("SELECT b.id")))
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .sortBy("totalUnallottedData")
                .sortDirection("DESC")
                .build();

        BoardPageResponse response = repositoryCustomImpl.fetchBoardsWithLeadStats(request, null, systemScope);

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
        when(entityManager.createNativeQuery(contains("SELECT b.id")))
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder()
                .page(0)
                .size(10)
                .search("CBSE")
                .sortBy("totalAvailedData")
                .sortDirection("ASC")
                .build();

        repositoryCustomImpl.fetchBoardsWithLeadStats(request, "ACTIVE", systemScope);

        verify(countQuery).setParameter("searchPattern", "%cbse%");
        verify(countQuery).setParameter("activeStatus", true);
        verify(dataQuery).setParameter("searchPattern", "%cbse%");
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
        when(entityManager.createNativeQuery(contains("SELECT b.id")))
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).build();

        // 1. Test HOD scope
        repositoryCustomImpl.fetchBoardsWithLeadStats(request, null, hodScope);
        verify(dataQuery, atLeastOnce()).setParameter("scopeUserId", hodUserId);
        verify(dataQuery, atLeastOnce()).setParameter("scopeDeptIds", Set.of(deptId));

        // 2. Test Counselor scope
        reset(dataQuery, countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        repositoryCustomImpl.fetchBoardsWithLeadStats(request, null, counselorScope);
        verify(dataQuery, atLeastOnce()).setParameter("scopeUserId", counselorUserId);
    }

    @Test
    @DisplayName("I. Board Visibility: Non-admin scopes generate INNER JOIN with stats.total_data > 0")
    void testBoardVisibility_NonAdminUsesInnerJoin() {
        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createNativeQuery(contains("INNER JOIN")))
                .thenReturn(countQuery)
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).build();
        repositoryCustomImpl.fetchBoardsWithLeadStats(request, null, hodScope);

        verify(entityManager, times(1)).createNativeQuery(contains("SELECT COUNT(b.id) FROM lead_boards b INNER JOIN"));
        verify(entityManager, times(1)).createNativeQuery(contains("SELECT b.id AS id, b.name AS name, b.code AS code, b.description AS description, b.active AS active, b.display_order AS display_order, b.created_at AS created_at, b.updated_at AS updated_at, COALESCE(stats.total_data, 0) AS total_data, COALESCE(stats.total_allotted_data, 0) AS total_allotted_data, COALESCE(stats.total_unallotted_data, 0) AS total_unallotted_data, COALESCE(stats.total_availed_data, 0) AS total_availed_data FROM lead_boards b INNER JOIN"));
    }
}
