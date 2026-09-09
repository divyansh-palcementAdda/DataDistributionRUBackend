package com.app.datadistribution.service;

import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.SentimentCategory;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.mapper.UserMapper;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusHistoryRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.LeadServiceImpl;
import com.app.datadistribution.service.impl.LocationServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.ILeadStatusTransitionService;
import com.app.datadistribution.service.interfaces.ILocationService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LeadPreferredStudyAndVisitPlanningTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadStatusRepository leadStatusRepository;

    @Mock
    private LeadStatusHistoryRepository leadStatusHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IUserDataScopeService dataScopeService;

    @Mock
    private ILeadDataScopeService leadDataScopeService;

    @Mock
    private ILeadStatusTransitionService leadStatusTransitionService;

    @Spy
    private LeadMapper leadMapper = Mappers.getMapper(LeadMapper.class);

    @Spy
    private ILocationService locationService = new LocationServiceImpl();

    @Mock
    private com.app.datadistribution.repository.ProgramRepository programRepository;

    @Mock
    private com.app.datadistribution.service.util.ProgramCourseResolver programCourseResolver;

    @InjectMocks
    private LeadServiceImpl leadService;

    private User counselor;
    private LeadStatus rawStatus;
    private LeadStatus connectedStatus;
    private LeadStatus interestedStatus;
    private UserDataScope globalScope;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(leadService, "locationService", locationService);
        ReflectionTestUtils.setField(leadService, "leadMapper", leadMapper);

        counselor = User.builder()
                .username("counselor1")
                .email("counselor1@example.com")
                .active(true)
                .build();
        counselor.setId(UUID.randomUUID());

        rawStatus = LeadStatus.builder()
                .name("RAW")
                .code("RAW")
                .active(true)
                .sentimentCategory(SentimentCategory.NEUTRAL)
                .build();
        rawStatus.setId(UUID.randomUUID());

        connectedStatus = LeadStatus.builder()
                .name("CONNECTED")
                .code("CONNECTED")
                .active(true)
                .sentimentCategory(SentimentCategory.POSITIVE)
                .build();
        connectedStatus.setId(UUID.randomUUID());

        interestedStatus = LeadStatus.builder()
                .name("INTERESTED")
                .code("INTERESTED")
                .active(true)
                .sentimentCategory(SentimentCategory.POSITIVE)
                .build();
        interestedStatus.setId(UUID.randomUUID());

        globalScope = UserDataScope.builder()
                .userId(counselor.getId())
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .build();

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken("counselor1", "password", Collections.emptyList());
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("counselor1")).thenReturn(Optional.of(counselor));
        when(userRepository.findByEmail("counselor1")).thenReturn(Optional.of(counselor));
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(globalScope);
        when(leadStatusRepository.findByNameIgnoreCase("RAW")).thenReturn(Optional.of(rawStatus));
        when(leadStatusRepository.findByNameIgnoreCase("Raw")).thenReturn(Optional.of(rawStatus));
        when(leadStatusRepository.findByNameIgnoreCase("Main Raw")).thenReturn(Optional.of(rawStatus));
        when(leadStatusRepository.findByCodeIgnoreCase("RAW")).thenReturn(Optional.of(rawStatus));
        when(leadStatusRepository.findByCodeIgnoreCase("Raw")).thenReturn(Optional.of(rawStatus));
        when(leadStatusRepository.findAll()).thenReturn(List.of(rawStatus));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> {
            Lead l = invocation.getArgument(0);
            if (l.getId() == null) {
                l.setId(UUID.randomUUID());
            }
            return l;
        });
    }

    @Test
    @DisplayName("Create Lead with valid Preferred State and City succeeds")
    void testCreateLeadWithValidPreferredStudyPlace() throws Exception {
        LeadRequest request = LeadRequest.builder()
                .fullName("Aarav Sharma")
                .phoneNumber("9876543210")
                .preferredStudyState("Madhya Pradesh")
                .preferredStudyCity("Indore")
                .build();

        LeadResponse response = leadService.create(request);
        assertNotNull(response);
        assertEquals("Madhya Pradesh", response.getPreferredStudyState());
        assertEquals("Indore", response.getPreferredStudyCity());
    }

    @Test
    @DisplayName("Create Lead with invalid city not in state throws BadRequestException")
    void testCreateLeadWithMismatchedCityThrowsException() {
        LeadRequest request = LeadRequest.builder()
                .fullName("Aarav Sharma")
                .phoneNumber("9876543210")
                .preferredStudyState("Madhya Pradesh")
                .preferredStudyCity("Mumbai") // Mumbai is in Maharashtra
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> leadService.create(request));
        assertTrue(ex.getMessage().contains("does not belong to state"));
    }

    @Test
    @DisplayName("Create Lead with city but missing state throws BadRequestException")
    void testCreateLeadWithCityWithoutStateThrowsException() {
        LeadRequest request = LeadRequest.builder()
                .fullName("Aarav Sharma")
                .phoneNumber("9876543210")
                .preferredStudyCity("Indore")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> leadService.create(request));
        assertTrue(ex.getMessage().contains("Preferred study state is required"));
    }

    @Test
    @DisplayName("Create Lead without preferred study place succeeds (optional)")
    void testCreateLeadWithoutPreferredStudyPlace() throws Exception {
        LeadRequest request = LeadRequest.builder()
                .fullName("Aarav Sharma")
                .phoneNumber("9876543210")
                .build();

        LeadResponse response = leadService.create(request);
        assertNotNull(response);
        assertNull(response.getPreferredStudyState());
        assertNull(response.getPreferredStudyCity());
    }

    @Test
    @DisplayName("Create Lead with Planning to Visit University enabled and valid date/time/remarks succeeds")
    void testCreateLeadWithVisitPlanning() throws Exception {
        LocalDate visitDate = LocalDate.now().plusDays(5);
        LocalTime visitTime = LocalTime.of(11, 30);
        String remarks = "Student visiting campus with parents.";

        LeadRequest request = LeadRequest.builder()
                .fullName("Neha Gupta")
                .phoneNumber("9876543211")
                .planningToVisitUniversity(true)
                .visitDate(visitDate)
                .visitTime(visitTime)
                .visitRemarks(remarks)
                .build();

        LeadResponse response = leadService.create(request);
        assertNotNull(response);
        assertTrue(Boolean.TRUE.equals(response.getPlanningToVisitUniversity()));
        assertEquals(visitDate, response.getVisitDate());
        assertEquals(visitTime, response.getVisitTime());
        assertEquals(remarks, response.getVisitRemarks());
    }

    @Test
    @DisplayName("Create Lead with Planning enabled but missing date throws BadRequestException")
    void testCreateLeadWithVisitPlanningMissingDateThrowsException() {
        LeadRequest request = LeadRequest.builder()
                .fullName("Neha Gupta")
                .phoneNumber("9876543211")
                .planningToVisitUniversity(true)
                .visitTime(LocalTime.of(11, 30))
                .visitRemarks("Visiting campus")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> leadService.create(request));
        assertTrue(ex.getMessage().contains("Visit date is required"));
    }

    @Test
    @DisplayName("Create Lead with Planning enabled but missing time throws BadRequestException")
    void testCreateLeadWithVisitPlanningMissingTimeThrowsException() {
        LeadRequest request = LeadRequest.builder()
                .fullName("Neha Gupta")
                .phoneNumber("9876543211")
                .planningToVisitUniversity(true)
                .visitDate(LocalDate.now().plusDays(2))
                .visitRemarks("Visiting campus")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> leadService.create(request));
        assertTrue(ex.getMessage().contains("Visit time is required"));
    }

    @Test
    @DisplayName("Create Lead with Planning enabled but missing remarks throws BadRequestException")
    void testCreateLeadWithVisitPlanningMissingRemarksThrowsException() {
        LeadRequest request = LeadRequest.builder()
                .fullName("Neha Gupta")
                .phoneNumber("9876543211")
                .planningToVisitUniversity(true)
                .visitDate(LocalDate.now().plusDays(2))
                .visitTime(LocalTime.of(10, 0))
                .visitRemarks("   ")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> leadService.create(request));
        assertTrue(ex.getMessage().contains("Visit remarks are required"));
    }

    @Test
    @DisplayName("Updating Lead from Visit Planning ON to OFF clears visit details")
    void testUpdateLeadDisableVisitPlanning() throws Exception {
        UUID leadId = UUID.randomUUID();
        Lead existingLead = Lead.builder()
                .leadCode("LEAD-101")
                .fullName("Rohan Verma")
                .phoneNumber("9123456780")
                .currentStatus(rawStatus)
                .planningToVisitUniversity(true)
                .visitDate(LocalDate.now().plusDays(3))
                .visitTime(LocalTime.of(14, 0))
                .visitRemarks("Campus visit scheduled")
                .build();
        existingLead.setId(leadId);

        when(leadRepository.findById(leadId)).thenReturn(Optional.of(existingLead));

        LeadRequest updateRequest = LeadRequest.builder()
                .fullName("Rohan Verma")
                .phoneNumber("9123456780")
                .planningToVisitUniversity(false)
                .build();

        LeadResponse response = leadService.update(leadId, updateRequest);
        assertNotNull(response);
        assertFalse(Boolean.TRUE.equals(response.getPlanningToVisitUniversity()));
        assertNull(response.getVisitDate());
        assertNull(response.getVisitTime());
        assertNull(response.getVisitRemarks());
    }

    @Test
    @DisplayName("Lead Status transition preserves Visit Planning and Preferred Study Place")
    void testStatusChangePreservesVisitPlanningAndPreferredStudyPlace() throws Exception {
        UUID leadId = UUID.randomUUID();
        LocalDate visitDate = LocalDate.now().plusDays(7);
        LocalTime visitTime = LocalTime.of(15, 30);
        String remarks = "Interested student visiting campus";

        Lead existingLead = Lead.builder()
                .leadCode("LEAD-202")
                .fullName("Priya Patel")
                .phoneNumber("9898989898")
                .currentStatus(connectedStatus)
                .preferredStudyState("Gujarat")
                .preferredStudyCity("Ahmedabad")
                .planningToVisitUniversity(true)
                .visitDate(visitDate)
                .visitTime(visitTime)
                .visitRemarks(remarks)
                .build();
        existingLead.setId(leadId);

        when(leadRepository.findById(leadId)).thenReturn(Optional.of(existingLead));
        when(leadStatusRepository.findById(interestedStatus.getId())).thenReturn(Optional.of(interestedStatus));
        when(leadStatusTransitionService.executeStatusTransition(any(Lead.class), any(LeadStatus.class), any(), any())).thenAnswer(inv -> {
            Lead l = inv.getArgument(0);
            LeadStatus s = inv.getArgument(1);
            l.setCurrentStatus(s);
            return l;
        });

        LeadRequest updateRequest = LeadRequest.builder()
                .fullName("Priya Patel")
                .phoneNumber("9898989898")
                .statusId(interestedStatus.getId())
                .preferredStudyState("Gujarat")
                .preferredStudyCity("Ahmedabad")
                .planningToVisitUniversity(true)
                .visitDate(visitDate)
                .visitTime(visitTime)
                .visitRemarks(remarks)
                .build();

        LeadResponse response = leadService.update(leadId, updateRequest);
        assertNotNull(response);
        assertEquals("Gujarat", response.getPreferredStudyState());
        assertEquals("Ahmedabad", response.getPreferredStudyCity());
        assertTrue(Boolean.TRUE.equals(response.getPlanningToVisitUniversity()));
        assertEquals(visitDate, response.getVisitDate());
        assertEquals(visitTime, response.getVisitTime());
        assertEquals(remarks, response.getVisitRemarks());
    }
}
