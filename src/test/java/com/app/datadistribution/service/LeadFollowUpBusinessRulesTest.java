package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.lead.CancelFollowUpRequest;
import com.app.datadistribution.dto.lead.CompleteFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.LeadFollowUpServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeadFollowUpBusinessRulesTest {

    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private ILeadDataScopeService leadDataScopeService;

    @InjectMocks
    private LeadFollowUpServiceImpl followUpService;

    private User counselorA;
    private User counselorB;
    private Department deptAdmissions;
    private Lead lead1;
    private Lead lead2;

    @BeforeEach
    void setUp() throws UnauthorizedException, BadRequestException {
        deptAdmissions = Department.builder().name("Admissions").code("ADM").active(true).build();
        deptAdmissions.setId(UUID.randomUUID());

        counselorA = User.builder().username("counselorA").active(true).build();
        counselorA.setId(UUID.randomUUID());

        counselorB = User.builder().username("counselorB").active(true).build();
        counselorB.setId(UUID.randomUUID());

        lead1 = Lead.builder()
                .leadCode("LEAD-001")
                .assignedTo(counselorA)
                .department(deptAdmissions)
                .build();
        lead1.setId(UUID.randomUUID());

        lead2 = Lead.builder()
                .leadCode("LEAD-002")
                .assignedTo(counselorA)
                .department(deptAdmissions)
                .build();
        lead2.setId(UUID.randomUUID());

        when(leadRepository.findByIdForUpdate(lead1.getId())).thenReturn(Optional.of(lead1));
        when(leadRepository.findById(lead1.getId())).thenReturn(Optional.of(lead1));
        when(leadRepository.findByIdForUpdate(lead2.getId())).thenReturn(Optional.of(lead2));
        when(leadRepository.findById(lead2.getId())).thenReturn(Optional.of(lead2));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        when(userRepository.findByUsername(counselorA.getUsername())).thenReturn(Optional.of(counselorA));
        when(userRepository.findByUsername(counselorB.getUsername())).thenReturn(Optional.of(counselorB));

        when(leadFollowUpRepository.save(any(LeadFollowUp.class))).thenAnswer(inv -> {
            LeadFollowUp f = inv.getArgument(0);
            if (f.getId() == null) f.setId(UUID.randomUUID());
            return f;
        });

        when(leadMapper.toDto(any(LeadFollowUp.class))).thenAnswer(inv -> {
            LeadFollowUp f = inv.getArgument(0);
            return LeadFollowUpResponse.builder()
                    .id(f.getId())
                    .followUpDate(f.getFollowUpDate())
                    .remarks(f.getRemarks())
                    .status(f.getStatus())
                    .completed(f.isCompleted())
                    .completedAt(f.getCompletedAt())
                    .build();
        });

        UserDataScope scope = UserDataScope.builder().scopeType(ScopeType.SELF).userId(counselorA.getId()).build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
    }

    private void mockAuth(User user) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(user.getUsername());
        when(auth.getPrincipal()).thenReturn(user.getUsername());

        SecurityContext sc = mock(SecurityContext.class);
        when(sc.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(sc);
    }

    @Test
    @DisplayName("Test 1: Assigned user schedules follow-up with valid remarks -> SUCCESS")
    void test1_AssignedUser_CreateFollowUp_Success() throws Exception {
        mockAuth(counselorA);
        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(lead1.getId())).thenReturn(false);

        LocalDateTime scheduledDate = LocalDateTime.now().plusDays(2);
        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .followUpDate(scheduledDate)
                .remarks("Student requested fee structure discussion on Wednesday")
                .build();

        LeadFollowUpResponse response = followUpService.createFollowUp(lead1.getId(), request);

        assertNotNull(response);
        assertEquals(FollowUpStatus.PENDING, response.getStatus());
        assertEquals("Student requested fee structure discussion on Wednesday", response.getRemarks());
        assertEquals(scheduledDate, lead1.getNextFollowUpDate());
    }

    @Test
    @DisplayName("Test 2: Non-assigned user attempts to schedule follow-up -> DENIED")
    void test2_NonAssignedUser_CreateFollowUp_Denied() {
        mockAuth(counselorB);
        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(lead1.getId())).thenReturn(false);

        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .followUpDate(LocalDateTime.now().plusDays(2))
                .remarks("Counselor B trying to schedule")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                followUpService.createFollowUp(lead1.getId(), request));

        assertTrue(ex.getMessage().contains("Only the currently assigned user can schedule"));
    }

    @Test
    @DisplayName("Test 3: Create follow-up with null / empty / blank remarks -> DENIED")
    void test3_CreateFollowUp_BlankRemarks_Denied() {
        mockAuth(counselorA);
        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(lead1.getId())).thenReturn(false);

        // Null remarks
        LeadFollowUpRequest req1 = LeadFollowUpRequest.builder().followUpDate(LocalDateTime.now().plusDays(1)).remarks(null).build();
        assertThrows(BadRequestException.class, () -> followUpService.createFollowUp(lead1.getId(), req1));

        // Empty remarks
        LeadFollowUpRequest req2 = LeadFollowUpRequest.builder().followUpDate(LocalDateTime.now().plusDays(1)).remarks("").build();
        assertThrows(BadRequestException.class, () -> followUpService.createFollowUp(lead1.getId(), req2));

        // Whitespace remarks
        LeadFollowUpRequest req3 = LeadFollowUpRequest.builder().followUpDate(LocalDateTime.now().plusDays(1)).remarks("    ").build();
        assertThrows(BadRequestException.class, () -> followUpService.createFollowUp(lead1.getId(), req3));
    }

    @Test
    @DisplayName("Test 4: Lead has PENDING follow-up -> New follow-up DENIED")
    void test4_LeadHasPendingFollowUp_NewFollowUpDenied() {
        mockAuth(counselorA);
        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(lead1.getId())).thenReturn(true);

        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .followUpDate(LocalDateTime.now().plusDays(3))
                .remarks("Second follow-up attempt")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                followUpService.createFollowUp(lead1.getId(), request));

        assertTrue(ex.getMessage().contains("A follow-up is already active for this lead"));
    }

    @Test
    @DisplayName("Test 5: Lead has UPCOMING follow-up -> New follow-up DENIED")
    void test5_LeadHasUpcomingFollowUp_NewFollowUpDenied() {
        mockAuth(counselorA);
        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(lead1.getId())).thenReturn(true);

        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .followUpDate(LocalDateTime.now().plusDays(5))
                .remarks("Another attempt")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                followUpService.createFollowUp(lead1.getId(), request));

        assertTrue(ex.getMessage().contains("A follow-up is already active for this lead"));
    }

    @Test
    @DisplayName("Test 6: Active follow-up is per-lead (Counselor can schedule on Lead 2 while Lead 1 has active)")
    void test6_ActiveFollowUp_IsPerLead() throws Exception {
        mockAuth(counselorA);
        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(lead1.getId())).thenReturn(true);
        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(lead2.getId())).thenReturn(false);

        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .followUpDate(LocalDateTime.now().plusDays(1))
                .remarks("Valid follow-up on lead 2")
                .build();

        LeadFollowUpResponse response = followUpService.createFollowUp(lead2.getId(), request);
        assertNotNull(response);
        assertEquals(FollowUpStatus.PENDING, response.getStatus());
    }

    @Test
    @DisplayName("Test 7: Complete PENDING follow-up with feedback -> SUCCESS")
    void test7_CompletePendingFollowUp_Success() throws Exception {
        mockAuth(counselorA);
        UUID followUpId = UUID.randomUUID();
        LeadFollowUp followUp = LeadFollowUp.builder()
                .lead(lead1)
                .status(FollowUpStatus.PENDING)
                .completed(false)
                .remarks("Initial follow-up")
                .build();
        followUp.setId(followUpId);

        when(leadFollowUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));

        LeadFollowUpResponse response = followUpService.completeFollowUp(followUpId, "Spoke with student, confirmed registration");

        assertNotNull(response);
        assertEquals(FollowUpStatus.COMPLETED, response.getStatus());
        assertTrue(response.isCompleted());
        assertNotNull(response.getCompletedAt());
        assertTrue(followUp.getRemarks().contains("Completion Feedback: Spoke with student, confirmed registration"));
    }

    @Test
    @DisplayName("Test 8: Complete UPCOMING follow-up with feedback -> SUCCESS")
    void test8_CompleteUpcomingFollowUp_Success() throws Exception {
        mockAuth(counselorA);
        UUID followUpId = UUID.randomUUID();
        LeadFollowUp followUp = LeadFollowUp.builder()
                .lead(lead1)
                .status(FollowUpStatus.UPCOMING)
                .completed(false)
                .remarks("Upcoming counseling")
                .build();
        followUp.setId(followUpId);

        when(leadFollowUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));

        LeadFollowUpResponse response = followUpService.completeFollowUp(followUpId, CompleteFollowUpRequest.builder().feedback("Counseling session completed").build());

        assertNotNull(response);
        assertEquals(FollowUpStatus.COMPLETED, response.getStatus());
        assertTrue(response.isCompleted());
    }

    @Test
    @DisplayName("Test 9: Complete follow-up without feedback (null / empty / blank) -> DENIED")
    void test9_CompleteWithoutFeedback_Denied() {
        mockAuth(counselorA);
        UUID followUpId = UUID.randomUUID();

        assertThrows(BadRequestException.class, () -> followUpService.completeFollowUp(followUpId, (String) null));
        assertThrows(BadRequestException.class, () -> followUpService.completeFollowUp(followUpId, ""));
        assertThrows(BadRequestException.class, () -> followUpService.completeFollowUp(followUpId, "   "));
        assertThrows(BadRequestException.class, () -> followUpService.completeFollowUp(followUpId, CompleteFollowUpRequest.builder().feedback("   ").build()));
    }

    @Test
    @DisplayName("Test 10: Cancel PENDING follow-up with feedback -> SUCCESS")
    void test10_CancelPendingFollowUp_Success() throws Exception {
        mockAuth(counselorA);
        UUID followUpId = UUID.randomUUID();
        LeadFollowUp followUp = LeadFollowUp.builder()
                .lead(lead1)
                .status(FollowUpStatus.PENDING)
                .completed(false)
                .remarks("Initial follow-up")
                .build();
        followUp.setId(followUpId);

        when(leadFollowUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));

        LeadFollowUpResponse response = followUpService.cancelFollowUp(followUpId, "Student requested cancellation as they joined another institute");

        assertNotNull(response);
        assertEquals(FollowUpStatus.CANCELLED, response.getStatus());
        assertNotNull(followUp.getCancelledAt());
        assertEquals("Student requested cancellation as they joined another institute", followUp.getCancellationRemarks());
    }

    @Test
    @DisplayName("Test 11: Cancel follow-up without feedback (null / empty / blank) -> DENIED")
    void test11_CancelWithoutFeedback_Denied() {
        mockAuth(counselorA);
        UUID followUpId = UUID.randomUUID();

        assertThrows(BadRequestException.class, () -> followUpService.cancelFollowUp(followUpId, (String) null));
        assertThrows(BadRequestException.class, () -> followUpService.cancelFollowUp(followUpId, ""));
        assertThrows(BadRequestException.class, () -> followUpService.cancelFollowUp(followUpId, "   "));
        assertThrows(BadRequestException.class, () -> followUpService.cancelFollowUp(followUpId, CancelFollowUpRequest.builder().feedback("   ").build()));
    }

    @Test
    @DisplayName("Test 12: Invalid state machine transitions (COMPLETED -> CANCELLED / CANCELLED -> COMPLETED) -> DENIED")
    void test12_InvalidStateTransitions_Denied() {
        mockAuth(counselorA);
        UUID followUpId = UUID.randomUUID();

        LeadFollowUp completedFollowUp = LeadFollowUp.builder()
                .lead(lead1)
                .status(FollowUpStatus.COMPLETED)
                .completed(true)
                .build();
        completedFollowUp.setId(followUpId);
        when(leadFollowUpRepository.findById(followUpId)).thenReturn(Optional.of(completedFollowUp));

        // Attempt to cancel an already completed follow-up -> DENIED
        BadRequestException ex1 = assertThrows(BadRequestException.class, () ->
                followUpService.cancelFollowUp(followUpId, "Trying to cancel completed follow-up"));
        assertTrue(ex1.getMessage().contains("Only PENDING or UPCOMING follow-ups can be cancelled"));

        // Attempt to complete an already cancelled follow-up -> DENIED
        LeadFollowUp cancelledFollowUp = LeadFollowUp.builder()
                .lead(lead1)
                .status(FollowUpStatus.CANCELLED)
                .build();
        cancelledFollowUp.setId(followUpId);
        when(leadFollowUpRepository.findById(followUpId)).thenReturn(Optional.of(cancelledFollowUp));

        BadRequestException ex2 = assertThrows(BadRequestException.class, () ->
                followUpService.completeFollowUp(followUpId, "Trying to complete cancelled follow-up"));
        assertTrue(ex2.getMessage().contains("Only PENDING or UPCOMING follow-ups can be marked as completed"));
    }

    @Test
    @DisplayName("Test 13: Data integrity: Follow-up cannot mutate Lead department or assigned user")
    void test13_DataIntegrity_Preserved() throws Exception {
        mockAuth(counselorA);
        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(lead1.getId())).thenReturn(false);

        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .followUpDate(LocalDateTime.now().plusDays(2))
                .remarks("Checking data integrity preservation")
                .build();

        followUpService.createFollowUp(lead1.getId(), request);

        // Lead department and assigned user must remain authoritative
        assertEquals(deptAdmissions, lead1.getDepartment());
        assertEquals(counselorA, lead1.getAssignedTo());
    }
}
