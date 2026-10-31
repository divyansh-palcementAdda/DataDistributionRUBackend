package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.app.datadistribution.dto.lead.LeadMatchResult;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.*;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.*;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.impl.LeadServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.ILeadDeduplicationService;
import com.app.datadistribution.service.interfaces.ILeadStatusTransitionService;
import com.app.datadistribution.service.interfaces.ILocationService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import com.app.datadistribution.service.util.LeadAcademicResolver;
import com.app.datadistribution.service.util.ProgramCourseResolver;
import jakarta.persistence.EntityManager;
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

@ExtendWith(MockitoExtension.class)
class LeadManualCreationDeduplicationTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadSourceRepository leadSourceRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private StreamRepository streamRepository;
    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LeadStatusHistoryRepository leadStatusHistoryRepository;
    @Mock
    private LeadFeedbackRepository leadFeedbackRepository;
    @Mock
    private LeadAvailedRepository leadAvailedRepository;
    @Mock
    private LeadAssignmentHistoryRepository leadAssignmentHistoryRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private ProgramCourseResolver programCourseResolver;
    @Mock
    private LeadAcademicResolver leadAcademicResolver;
    @Mock
    private IUserDataScopeService dataScopeService;
    @Mock
    private ILeadDataScopeService leadDataScopeService;
    @Mock
    private ILeadStatusTransitionService leadStatusTransitionService;
    @Mock
    private com.app.datadistribution.integration.cms.service.IStudentVerificationService studentVerificationService;
    @Mock
    private ILocationService locationService;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ILeadDeduplicationService deduplicationService;

    @InjectMocks
    private LeadServiceImpl leadService;

    private User currentUser;
    private LeadStatus defaultStatus;
    private LeadSource sourceWeb;
    private Course courseBBA;
    private Course courseMBA;

    @BeforeEach
    void setUp() throws UnauthorizedException, BadRequestException {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("counselor1");
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        currentUser = User.builder().username("counselor1").active(true).build();
        currentUser.setId(UUID.randomUUID());
        lenient().when(userRepository.findByUsername("counselor1")).thenReturn(Optional.of(currentUser));

        UserDataScope scope = UserDataScope.builder()
                .scopeType(UserDataScope.ScopeType.SYSTEM)
                .userId(currentUser.getId())
                .build();
        lenient().when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);

        defaultStatus = LeadStatus.builder().name("Raw").code("RAW").active(true).build();
        lenient().when(leadStatusRepository.findByCodeIgnoreCase("RAW")).thenReturn(Optional.of(defaultStatus));

        sourceWeb = LeadSource.builder().name("Website - Fees Inquiry").code("WEB_FEES").active(true).build();
        sourceWeb.setId(UUID.randomUUID());
        lenient().when(leadSourceRepository.findById(eq(sourceWeb.getId()))).thenReturn(Optional.of(sourceWeb));

        courseBBA = Course.builder().courseName("BBA").build();
        courseBBA.setId(UUID.randomUUID());
        courseMBA = Course.builder().courseName("MBA").build();
        courseMBA.setId(UUID.randomUUID());
        lenient().when(courseRepository.findById(eq(courseBBA.getId()))).thenReturn(Optional.of(courseBBA));
        lenient().when(courseRepository.findById(eq(courseMBA.getId()))).thenReturn(Optional.of(courseMBA));

        lenient().when(leadAcademicResolver.resolveLeadAcademicMappings(any(), any(), any(), any()))
                .thenReturn(com.app.datadistribution.service.dto.LeadAcademicResolutionResult.builder()
                        .programs(new HashSet<>())
                        .interestedCourses(new HashSet<>())
                        .build());

        lenient().when(leadRepository.findAllActivePhoneNumbers()).thenReturn(Collections.emptyList());

        // Deduplication mocks
        lenient().when(deduplicationService.normalizePhone(any())).thenAnswer(inv -> {
            String p = inv.getArgument(0);
            return p != null ? p.replaceAll("[^0-9]", "") : null;
        });
        lenient().when(deduplicationService.executeWithPhoneLock(any(), any())).thenAnswer(inv -> {
            java.util.function.Supplier<?> action = inv.getArgument(1);
            return action.get();
        });
        leadService.setLeadDeduplicationService(deduplicationService);
    }

    @Test
    @DisplayName("Manual Lead Creation: No match creates a brand new Lead record")
    void testCreate_NoMatch_CreatesNewLead() throws Exception {
        LeadRequest request = LeadRequest.builder()
                .fullName("Akshat Gupta")
                .phoneNumber("9876543210")
                .email("akshat@example.com")
                .leadSourceIds(List.of(sourceWeb.getId()))
                .interestedCourseIds(List.of(courseBBA.getId()))
                .build();

        when(deduplicationService.matchLead(any(), any(), any(), any(), any(), any()))
                .thenReturn(LeadMatchResult.noMatch("No duplicates"));

        when(leadMapper.toEntity(any(LeadRequest.class))).thenAnswer(inv -> new Lead());

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> {
            Lead lead = inv.getArgument(0);
            lead.setId(UUID.randomUUID());
            return lead;
        });

        LeadResponse mockResponse = new LeadResponse();
        mockResponse.setFullName("Akshat Gupta");
        mockResponse.setPhoneNumber("9876543210");
        when(leadMapper.toDto(any(Lead.class))).thenReturn(mockResponse);

        LeadResponse response = leadService.create(request);

        assertNotNull(response);
        assertFalse(response.isExistingLeadMatched());
        assertEquals("CREATED", response.getCreationOutcome());
        verify(leadRepository, atLeastOnce()).save(any(Lead.class));
        verify(deduplicationService, never()).enrichAndSaveMatchedLead(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Manual Lead Creation: Exact match reuses existing lead and returns matched outcome")
    void testCreate_ExactMatch_ReusesExistingLead() throws Exception {
        Lead existingLead = Lead.builder()
                .leadCode("LEAD-1001")
                .fullName("Akshat Gupta")
                .phoneNumber("9876543210")
                .email("akshat@example.com")
                .leadSources(new HashSet<>(Set.of(sourceWeb)))
                .interestedCourses(new HashSet<>(Set.of(courseBBA)))
                .currentStatus(defaultStatus)
                .build();
        existingLead.setId(UUID.randomUUID());

        LeadRequest request = LeadRequest.builder()
                .fullName("Akshat Gupta")
                .phoneNumber("9876543210")
                .email("akshat@example.com")
                .leadSourceIds(List.of(sourceWeb.getId()))
                .interestedCourseIds(List.of(courseBBA.getId()))
                .build();

        LeadMatchResult matchResult = LeadMatchResult.builder()
                .status(LeadMatchResult.MatchStatus.EXACT_MATCH)
                .matchedLead(existingLead)
                .matchReason("Exact match on Rule A")
                .build();

        when(deduplicationService.matchLead(any(), any(), any(), any(), any(), any()))
                .thenReturn(matchResult);

        when(deduplicationService.enrichAndSaveMatchedLead(eq(existingLead), eq(request), eq(matchResult), eq(currentUser), any()))
                .thenReturn(existingLead);

        LeadResponse mockResponse = new LeadResponse();
        mockResponse.setFullName("Akshat Gupta");
        mockResponse.setLeadCode("LEAD-1001");
        when(leadMapper.toDto(existingLead)).thenReturn(mockResponse);

        LeadResponse response = leadService.create(request);

        assertNotNull(response);
        assertTrue(response.isExistingLeadMatched());
        assertEquals("MATCHED", response.getCreationOutcome());
        verify(deduplicationService).enrichAndSaveMatchedLead(eq(existingLead), eq(request), eq(matchResult), eq(currentUser), any());
    }

    @Test
    @DisplayName("Manual Lead Creation: Multi-Course & Multi-Source addition retains all historical associations")
    void testCreate_MatchWithNewCourseAndSource_PreservesMultipleAssociations() throws Exception {
        Lead existingLead = Lead.builder()
                .leadCode("LEAD-1002")
                .fullName("Akshat Gupta")
                .phoneNumber("9876543210")
                .email("akshat@example.com")
                .leadSources(new HashSet<>(Set.of(sourceWeb)))
                .interestedCourses(new HashSet<>(Set.of(courseBBA)))
                .currentStatus(defaultStatus)
                .build();
        existingLead.setId(UUID.randomUUID());

        LeadSource sourceWalkin = LeadSource.builder().name("Walk-in").code("WALKIN").active(true).build();
        sourceWalkin.setId(UUID.randomUUID());
        when(leadSourceRepository.findById(eq(sourceWalkin.getId()))).thenReturn(Optional.of(sourceWalkin));

        LeadRequest request = LeadRequest.builder()
                .fullName("Akshat Gupta")
                .phoneNumber("9876543210")
                .email("akshat@example.com")
                .leadSourceIds(List.of(sourceWalkin.getId()))
                .interestedCourseIds(List.of(courseMBA.getId()))
                .build();

        LeadMatchResult matchResult = LeadMatchResult.builder()
                .status(LeadMatchResult.MatchStatus.EXACT_MATCH)
                .matchedLead(existingLead)
                .newCoursesToAdd(Set.of(courseMBA))
                .newSourcesToAdd(Set.of(sourceWalkin))
                .matchReason("Matched person across courses & sources (Rules E & F)")
                .build();

        when(deduplicationService.matchLead(any(), any(), any(), any(), any(), any()))
                .thenReturn(matchResult);

        // When enrichAndSaveMatchedLead executes, it adds courseMBA and sourceWalkin
        when(deduplicationService.enrichAndSaveMatchedLead(eq(existingLead), eq(request), eq(matchResult), eq(currentUser), any()))
                .thenAnswer(inv -> {
                    existingLead.getInterestedCourses().addAll(matchResult.getNewCoursesToAdd());
                    existingLead.getLeadSources().addAll(matchResult.getNewSourcesToAdd());
                    return existingLead;
                });

        LeadResponse mockResponse = new LeadResponse();
        mockResponse.setFullName("Akshat Gupta");
        mockResponse.setLeadCode("LEAD-1002");
        when(leadMapper.toDto(existingLead)).thenReturn(mockResponse);

        LeadResponse response = leadService.create(request);

        assertNotNull(response);
        assertTrue(response.isExistingLeadMatched());
        assertEquals("MATCHED", response.getCreationOutcome());

        // Verify that existing courses and sources are preserved alongside the newly associated ones
        assertEquals(2, existingLead.getInterestedCourses().size());
        assertTrue(existingLead.getInterestedCourses().contains(courseBBA));
        assertTrue(existingLead.getInterestedCourses().contains(courseMBA));

        assertEquals(2, existingLead.getLeadSources().size());
        assertTrue(existingLead.getLeadSources().contains(sourceWeb));
        assertTrue(existingLead.getLeadSources().contains(sourceWalkin));
    }
}

