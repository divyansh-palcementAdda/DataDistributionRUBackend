package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadAvailedResponse;
import com.app.datadistribution.dto.lead.LeadPageResponse;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAssignmentHistory;
import com.app.datadistribution.entity.LeadAvailed;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.*;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.LeadServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

@ExtendWith(MockitoExtension.class)
class LeadAvailedTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadSourceRepository leadSourceRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private BoardRepository boardRepository;
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
    private IUserDataScopeService dataScopeService;
    @Mock
    private ILeadDataScopeService leadDataScopeService;
    @Mock
    private LeadMapper leadMapper;

    @InjectMocks
    private LeadServiceImpl leadService;

    private UUID leadId;
    private Lead lead;
    private User counselorA;
    private User counselorB;
    private LeadStatus statusRaw;

    @BeforeEach
    void setUp() {
        leadId = UUID.randomUUID();

        counselorA = User.builder().username("counselorA").build();
        counselorA.setId(UUID.randomUUID());

        counselorB = User.builder().username("counselorB").build();
        counselorB.setId(UUID.randomUUID());

        statusRaw = LeadStatus.builder().name("Raw").code("RAW").active(true).build();
        statusRaw.setId(UUID.randomUUID());

        lead = Lead.builder()
                .leadCode("LEAD-5001")
                .fullName("Alice Student")
                .phoneNumber("9876500001")
                .currentStatus(statusRaw)
                .assignedTo(counselorA)
                .build();
        lead.setId(leadId);
    }

    private void mockSecurityContext(User user) {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn(user.getUsername());
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    @Test
    void testMarkLeadAsAvailed_Success() throws Exception {
        mockSecurityContext(counselorA);

        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(leadAvailedRepository.findByLeadIdAndAvailedByUserIdAndIsDeletedFalse(leadId, counselorA.getId()))
                .thenReturn(Optional.empty());
        when(leadAssignmentHistoryRepository.findByLeadIdOrderByCreatedAtDesc(leadId))
                .thenReturn(Collections.emptyList());

        when(leadAvailedRepository.save(any(LeadAvailed.class))).thenAnswer(inv -> {
            LeadAvailed la = inv.getArgument(0);
            la.setId(UUID.randomUUID());
            return la;
        });

        when(leadMapper.toDto(any(LeadAvailed.class))).thenAnswer(inv -> {
            LeadAvailed la = inv.getArgument(0);
            return LeadAvailedResponse.builder()
                    .id(la.getId())
                    .leadId(leadId)
                    .leadCode(lead.getLeadCode())
                    .isAvailed(true)
                    .availedBy(UserResponse.builder().id(counselorA.getId()).username(counselorA.getUsername()).build())
                    .availedAt(la.getAvailedAt())
                    .build();
        });

        LeadAvailedResponse response = leadService.markLeadAsAvailed(leadId);

        assertNotNull(response);
        assertTrue(response.isAvailed());
        assertEquals(counselorA.getId(), response.getAvailedBy().getId());
        assertNotNull(response.getAvailedAt());

        ArgumentCaptor<LeadAvailed> captor = ArgumentCaptor.forClass(LeadAvailed.class);
        verify(leadAvailedRepository, times(1)).save(captor.capture());
        LeadAvailed captured = captor.getValue();
        assertEquals(lead, captured.getLead());
        assertEquals(counselorA, captured.getAvailedByUser());
        assertNotNull(captured.getAvailedAt());
    }

    @Test
    void testMarkLeadAsAvailed_UnassignedLead_ThrowsBadRequestException() throws Exception {
        mockSecurityContext(counselorA);

        lead.setAssignedTo(null);
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> leadService.markLeadAsAvailed(leadId));
        assertTrue(ex.getMessage().contains("must be assigned"));
        verify(leadAvailedRepository, never()).save(any(LeadAvailed.class));
    }

    @Test
    void testMarkLeadAsAvailed_AnotherUser_ThrowsUnauthorizedException() throws Exception {
        mockSecurityContext(counselorB); // Logged in as counselorB, but lead assigned to counselorA

        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> leadService.markLeadAsAvailed(leadId));
        assertTrue(ex.getMessage().contains("Only the currently assigned user"));
        verify(leadAvailedRepository, never()).save(any(LeadAvailed.class));
    }

    @Test
    void testMarkLeadAsAvailed_Idempotent_DuplicateCallsDoNotCreateMultipleRecords() throws Exception {
        mockSecurityContext(counselorA);

        LeadAvailed existingRecord = LeadAvailed.builder()
                .lead(lead)
                .availedByUser(counselorA)
                .availedAt(LocalDateTime.now().minusHours(1))
                .build();
        existingRecord.setId(UUID.randomUUID());

        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(leadAvailedRepository.findByLeadIdAndAvailedByUserIdAndIsDeletedFalse(leadId, counselorA.getId()))
                .thenReturn(Optional.of(existingRecord));

        when(leadMapper.toDto(existingRecord)).thenReturn(LeadAvailedResponse.builder()
                .id(existingRecord.getId())
                .leadId(leadId)
                .isAvailed(true)
                .availedBy(UserResponse.builder().id(counselorA.getId()).build())
                .availedAt(existingRecord.getAvailedAt())
                .build());

        LeadAvailedResponse response = leadService.markLeadAsAvailed(leadId);

        assertNotNull(response);
        assertTrue(response.isAvailed());
        assertEquals(existingRecord.getAvailedAt(), response.getAvailedAt());
        // Verify save was NEVER called because it was already availed
        verify(leadAvailedRepository, never()).save(any(LeadAvailed.class));
    }

    @Test
    void testMarkLeadAsAvailed_LeadNotFound_ThrowsResourcesNotFoundException() {
        mockSecurityContext(counselorA);

        UUID nonExistentId = UUID.randomUUID();
        when(leadRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(ResourcesNotFoundException.class, () -> leadService.markLeadAsAvailed(nonExistentId));
    }

    @Test
    void testGetAllLeads_BatchEnrichesAvailedStatus() throws Exception {
        UserDataScope scope = UserDataScope.builder()
                .userId(counselorA.getId())
                .scopeType(ScopeType.SELF)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        when(leadDataScopeService.getLeadScopeSpecification(scope)).thenReturn(mock(Specification.class));

        Lead lead2 = Lead.builder()
                .leadCode("LEAD-5002")
                .fullName("Bob Student")
                .phoneNumber("9876500002")
                .currentStatus(statusRaw)
                .assignedTo(counselorA)
                .build();
        lead2.setId(UUID.randomUUID());

        List<Lead> leads = List.of(lead, lead2);
        Page<Lead> page = new PageImpl<>(leads);
        when(leadRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // lead 1 is availed, lead 2 is unavailed
        LeadAvailed availed1 = LeadAvailed.builder()
                .lead(lead)
                .availedByUser(counselorA)
                .availedAt(LocalDateTime.now())
                .build();
        availed1.setId(UUID.randomUUID());

        when(leadAvailedRepository.findByLeadIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of(availed1));

        when(leadMapper.toDto(lead)).thenReturn(LeadResponse.builder().id(lead.getId()).leadCode("LEAD-5001").build());
        when(leadMapper.toDto(lead2)).thenReturn(LeadResponse.builder().id(lead2.getId()).leadCode("LEAD-5002").build());

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        LeadPageResponse response = leadService.getAllLeads(
                pageRequest, null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        assertNotNull(response);
        assertEquals(2, response.getContent().size());

        LeadResponse dto1 = response.getContent().get(0);
        assertTrue(dto1.isAvailed(), "Lead 1 should be marked as availed");
        assertNotNull(dto1.getAvailedAt());

        LeadResponse dto2 = response.getContent().get(1);
        assertFalse(dto2.isAvailed(), "Lead 2 should NOT be marked as availed");
        assertNull(dto2.getAvailedAt());
    }

    @Test
    void testReassignmentLifecycle_NewAssigneeCanMarkAsAvailed() throws Exception {
        // Step 1: Lead originally availed by Counselor A
        LeadAvailed historyRecordA = LeadAvailed.builder()
                .lead(lead)
                .availedByUser(counselorA)
                .availedAt(LocalDateTime.now().minusDays(2))
                .build();
        historyRecordA.setId(UUID.randomUUID());

        // Step 2: Lead is reassigned to Counselor B
        lead.setAssignedTo(counselorB);

        // Step 3: Counselor B logs in and marks the lead as Availed for Counselor B's assignment lifecycle
        mockSecurityContext(counselorB);

        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        // For counselor B, no availed record exists yet
        when(leadAvailedRepository.findByLeadIdAndAvailedByUserIdAndIsDeletedFalse(leadId, counselorB.getId()))
                .thenReturn(Optional.empty());

        LeadAssignmentHistory assignmentHistory = LeadAssignmentHistory.builder()
                .lead(lead)
                .oldAssignedUser(counselorA)
                .newAssignedUser(counselorB)
                .build();
        when(leadAssignmentHistoryRepository.findByLeadIdOrderByCreatedAtDesc(leadId))
                .thenReturn(List.of(assignmentHistory));

        when(leadAvailedRepository.save(any(LeadAvailed.class))).thenAnswer(inv -> {
            LeadAvailed la = inv.getArgument(0);
            la.setId(UUID.randomUUID());
            return la;
        });

        when(leadMapper.toDto(any(LeadAvailed.class))).thenAnswer(inv -> {
            LeadAvailed la = inv.getArgument(0);
            return LeadAvailedResponse.builder()
                    .id(la.getId())
                    .leadId(leadId)
                    .isAvailed(true)
                    .availedBy(UserResponse.builder().id(counselorB.getId()).username(counselorB.getUsername()).build())
                    .availedAt(la.getAvailedAt())
                    .build();
        });

        LeadAvailedResponse response = leadService.markLeadAsAvailed(leadId);

        assertNotNull(response);
        assertTrue(response.isAvailed());
        assertEquals(counselorB.getId(), response.getAvailedBy().getId());

        ArgumentCaptor<LeadAvailed> captor = ArgumentCaptor.forClass(LeadAvailed.class);
        verify(leadAvailedRepository, times(1)).save(captor.capture());
        LeadAvailed captured = captor.getValue();
        assertEquals(counselorB, captured.getAvailedByUser());
        assertEquals(assignmentHistory, captured.getAssignmentHistory());
    }
}
