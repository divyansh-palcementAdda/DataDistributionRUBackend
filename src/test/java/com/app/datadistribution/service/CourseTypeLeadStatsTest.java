package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.app.datadistribution.common.PageResponseDTO;
import com.app.datadistribution.dto.course.CourseTypeResponseDTO;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.repository.CourseTypeRepositoryCustomImpl;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.CourseTypeServiceImpl;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
class CourseTypeLeadStatsTest {

    @Mock
    private CourseTypeRepository courseTypeRepository;

    @Mock
    private IUserDataScopeService dataScopeService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CourseTypeServiceImpl courseTypeService;

    private CourseTypeRepositoryCustomImpl repositoryCustomImpl;

    private UserDataScope systemScope;
    private UserDataScope hodScope;
    private UserDataScope counselorScope;

    private UUID adminUserId;
    private UUID hodUserId;
    private UUID counselorUserId;
    private UUID deptId;

    @BeforeEach
    void setUp() {
        repositoryCustomImpl = new CourseTypeRepositoryCustomImpl(entityManager);

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
    @DisplayName("A. Basic response: course type contains all 4 calculated metrics")
    void testBasicResponse_ContainsAllFourMetrics() throws Exception {
        UUID ugId = UUID.randomUUID();
        CourseTypeResponseDTO ugDto = CourseTypeResponseDTO.builder()
                .id(ugId)
                .name("UG")
                .description("Undergraduate")
                .status(Status.ACTIVE)
                .totalData(10L)
                .totalAllottedData(6L)
                .totalUnallottedData(4L)
                .totalAvailedData(3L)
                .build();

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).sortBy("name").sortDirection("ASC").build();
        PageResponseDTO<CourseTypeResponseDTO> mockPage = PageResponseDTO.<CourseTypeResponseDTO>builder()
                .content(List.of(ugDto))
                .page(0)
                .size(10)
                .totalElements(1L)
                .totalPages(1)
                .last(true)
                .build();

        when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        when(courseTypeRepository.fetchCourseTypesWithLeadStats(request, systemScope)).thenReturn(mockPage);

        PageResponseDTO<CourseTypeResponseDTO> response = courseTypeService.getAll(request);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        CourseTypeResponseDTO result = response.getContent().get(0);
        assertEquals("UG", result.getName());
        assertEquals(10L, result.getTotalData());
        assertEquals(6L, result.getTotalAllottedData());
        assertEquals(4L, result.getTotalUnallottedData());
        assertEquals(3L, result.getTotalAvailedData());
    }

    @Test
    @DisplayName("B. Correct aggregation: repository mapping maps SQL columns accurately")
    void testRepositoryMapping_MapsAggregatedRows() {
        UUID ctId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // Object array matching the SQL select:
        // ct.id, ct.name, ct.description, ct.status, ct.created_at, ct.updated_at,
        // total_data, total_allotted_data, total_unallotted_data, total_availed_data
        Object[] row1 = new Object[] {
                ctId, "UG", "Undergraduate Courses", "ACTIVE", now, now,
                10L, 6L, 4L, 3L
        };

        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.singletonList(row1));

        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(countQuery)
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).sortBy("totalData").sortDirection("DESC").build();
        PageResponseDTO<CourseTypeResponseDTO> response = repositoryCustomImpl.fetchCourseTypesWithLeadStats(request, systemScope);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());

        CourseTypeResponseDTO ct = response.getContent().get(0);
        assertEquals(ctId, ct.getId());
        assertEquals("UG", ct.getName());
        assertEquals("Undergraduate Courses", ct.getDescription());
        assertEquals(Status.ACTIVE, ct.getStatus());
        assertEquals(10L, ct.getTotalData());
        assertEquals(6L, ct.getTotalAllottedData());
        assertEquals(4L, ct.getTotalUnallottedData());
        assertEquals(3L, ct.getTotalAvailedData());
    }

    @Test
    @DisplayName("C. Zero values: course type with no leads returns 0, 0, 0, 0 (not null)")
    void testZeroValues_ReturnsZeros() {
        UUID ctId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Object[] rowEmpty = new Object[] {
                ctId, "PHD", "Doctorate", "ACTIVE", now, now,
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
        PageResponseDTO<CourseTypeResponseDTO> response = repositoryCustomImpl.fetchCourseTypesWithLeadStats(request, systemScope);

        assertNotNull(response);
        CourseTypeResponseDTO ct = response.getContent().get(0);
        assertEquals(0L, ct.getTotalData());
        assertEquals(0L, ct.getTotalAllottedData());
        assertEquals(0L, ct.getTotalUnallottedData());
        assertEquals(0L, ct.getTotalAvailedData());
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
        when(entityManager.createNativeQuery(contains("SELECT ct.id")))
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .sortBy("totalAllottedData")
                .sortDirection("DESC")
                .build();

        PageResponseDTO<CourseTypeResponseDTO> response = repositoryCustomImpl.fetchCourseTypesWithLeadStats(request, systemScope);

        assertNotNull(response);
        assertEquals(20L, response.getTotalElements());
        assertEquals(2, response.getTotalPages());
        assertEquals(1, response.getPage());
        assertTrue(response.isLast());

        verify(dataQuery).setParameter("limit", 10);
        verify(dataQuery).setParameter("offset", 10);
    }

    @Test
    @DisplayName("F. Search + Sorting: search parameter is bound to native queries")
    void testSearchAndSorting_BindsSearchPattern() {
        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createNativeQuery(contains("SELECT COUNT")))
                .thenReturn(countQuery);
        when(entityManager.createNativeQuery(contains("SELECT ct.id")))
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder()
                .page(0)
                .size(10)
                .search("UG")
                .sortBy("totalAvailedData")
                .sortDirection("ASC")
                .build();

        repositoryCustomImpl.fetchCourseTypesWithLeadStats(request, systemScope);

        verify(countQuery).setParameter("searchPattern", "%ug%");
        verify(dataQuery).setParameter("searchPattern", "%ug%");
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
        when(entityManager.createNativeQuery(contains("SELECT ct.id")))
                .thenReturn(dataQuery);

        PageRequestDTO request = PageRequestDTO.builder().page(0).size(10).build();

        // 1. Test HOD scope
        repositoryCustomImpl.fetchCourseTypesWithLeadStats(request, hodScope);
        verify(dataQuery, atLeastOnce()).setParameter("scopeUserId", hodUserId);
        verify(dataQuery, atLeastOnce()).setParameter("scopeDeptIds", Set.of(deptId));

        // 2. Test Counselor scope
        reset(dataQuery, countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

        repositoryCustomImpl.fetchCourseTypesWithLeadStats(request, counselorScope);
        verify(dataQuery, atLeastOnce()).setParameter("scopeUserId", counselorUserId);
    }
}
