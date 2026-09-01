package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.datadistribution.dto.segregation.CourseTypeSegregationDTO;
import com.app.datadistribution.dto.segregation.LeadStatusAnalyticsDTO;
import com.app.datadistribution.dto.segregation.SegregationMatrixResponseDTO;
import com.app.datadistribution.dto.segregation.UserSegregationAnalyticsDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.repository.BoardRepository;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.repository.DataSegregationRepository;
import com.app.datadistribution.repository.GradeRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.DataSegregationServiceImpl;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

@ExtendWith(MockitoExtension.class)
public class DataSegregationServiceTest {

    @Mock
    private DataSegregationRepository segregationRepository;

    @Mock
    private IUserDataScopeService dataScopeService;

    @Mock
    private CourseTypeRepository courseTypeRepository;

    @Mock
    private LeadSourceRepository leadSourceRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private GradeRepository gradeRepository;

    @InjectMocks
    private DataSegregationServiceImpl segregationService;

    private UserDataScope testScope;
    private UUID courseTypeId;
    private UUID leadSourceId;
    private UUID boardId;
    private UUID gradeId;

    @BeforeEach
    void setUp() {
        testScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(UUID.randomUUID())
                .isAdmin(true)
                .build();

        courseTypeId = UUID.randomUUID();
        leadSourceId = UUID.randomUUID();
        boardId = UUID.randomUUID();
        gradeId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getCourseTypesSummary returns aggregated course types")
    void testGetCourseTypesSummary() throws Exception {
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(testScope);
        List<CourseTypeSegregationDTO> mockList = List.of(
                CourseTypeSegregationDTO.builder().id(courseTypeId).name("UG").totalLeads(50).build()
        );
        when(segregationRepository.fetchCourseTypeSummary(testScope)).thenReturn(mockList);

        List<CourseTypeSegregationDTO> result = segregationService.getCourseTypesSummary();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("UG", result.get(0).getName());
        assertEquals(50, result.get(0).getTotalLeads());
    }

    @Test
    @DisplayName("getSegregationMatrix throws BadRequestException if courseTypeId is null")
    void testGetSegregationMatrix_NullCourseTypeId() {
        assertThrows(BadRequestException.class, () -> segregationService.getSegregationMatrix(null, null, null, null));
    }

    @Test
    @DisplayName("getSegregationMatrix throws ResourcesNotFoundException if courseTypeId is invalid")
    void testGetSegregationMatrix_InvalidCourseTypeId() {
        when(courseTypeRepository.existsById(courseTypeId)).thenReturn(false);
        assertThrows(ResourcesNotFoundException.class, () -> segregationService.getSegregationMatrix(courseTypeId, null, null, null));
    }

    @Test
    @DisplayName("getSegregationMatrix returns matrix response successfully")
    void testGetSegregationMatrix_Success() throws Exception {
        when(courseTypeRepository.existsById(courseTypeId)).thenReturn(true);
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(testScope);

        SegregationMatrixResponseDTO mockResponse = SegregationMatrixResponseDTO.builder()
                .courseTypeId(courseTypeId)
                .courseTypeName("UG")
                .totalLeads(100)
                .allottedLeads(70)
                .unallottedLeads(30)
                .availedLeads(15)
                .build();

        when(segregationRepository.fetchSegregationMatrix(eq(courseTypeId), eq(null), eq(null), eq(null), eq(testScope)))
                .thenReturn(mockResponse);

        SegregationMatrixResponseDTO result = segregationService.getSegregationMatrix(courseTypeId, null, null, null);
        assertNotNull(result);
        assertEquals("UG", result.getCourseTypeName());
        assertEquals(100, result.getTotalLeads());
        assertEquals(70, result.getAllottedLeads());
        assertEquals(30, result.getUnallottedLeads());
        assertEquals(15, result.getAvailedLeads());
    }

    @Test
    @DisplayName("getUserAnalytics returns user analytics response successfully")
    void testGetUserAnalytics_Success() throws Exception {
        when(courseTypeRepository.existsById(courseTypeId)).thenReturn(true);
        when(leadSourceRepository.existsById(leadSourceId)).thenReturn(true);
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(testScope);

        UserSegregationAnalyticsDTO mockResponse = UserSegregationAnalyticsDTO.builder()
                .courseTypeId(courseTypeId)
                .leadSourceId(leadSourceId)
                .build();

        when(segregationRepository.fetchUserAnalytics(eq(courseTypeId), eq(leadSourceId), eq(null), eq(null), eq(testScope)))
                .thenReturn(mockResponse);

        UserSegregationAnalyticsDTO result = segregationService.getUserAnalytics(courseTypeId, leadSourceId, null, null);
        assertNotNull(result);
        assertEquals(courseTypeId, result.getCourseTypeId());
        assertEquals(leadSourceId, result.getLeadSourceId());
    }

    @Test
    @DisplayName("getLeadStatusAnalytics returns lead status analytics successfully")
    void testGetLeadStatusAnalytics_Success() throws Exception {
        when(courseTypeRepository.existsById(courseTypeId)).thenReturn(true);
        when(leadSourceRepository.existsById(leadSourceId)).thenReturn(true);
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(testScope);

        List<LeadStatusAnalyticsDTO> mockResponse = List.of(
                LeadStatusAnalyticsDTO.builder().name("RAW").code("RAW").count(20).build(),
                LeadStatusAnalyticsDTO.builder().name("CONNECTED").code("CONNECTED").count(15).build()
        );

        when(segregationRepository.fetchLeadStatusAnalytics(eq(courseTypeId), eq(leadSourceId), eq(null), eq(null), eq(testScope)))
                .thenReturn(mockResponse);

        List<LeadStatusAnalyticsDTO> result = segregationService.getLeadStatusAnalytics(courseTypeId, leadSourceId, null, null);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("RAW", result.get(0).getCode());
        assertEquals(20, result.get(0).getCount());
    }
}
