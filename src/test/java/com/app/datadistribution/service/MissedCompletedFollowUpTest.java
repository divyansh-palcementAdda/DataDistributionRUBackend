package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.dto.lead.RescheduleFollowUpRequest;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusHistoryRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.LeadFollowUpServiceImpl;
import com.app.datadistribution.service.impl.LeadStatusTransitionServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadActionEnforcementService;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.ILeadStatusTransitionService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MissedCompletedFollowUpTest {

    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private LeadStatusHistoryRepository leadStatusHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private ILeadDataScopeService leadDataScopeService;
    @Mock
    private ILeadStatusTransitionService leadStatusTransitionService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ILeadActionEnforcementService leadActionEnforcementService;

    @InjectMocks
    private LeadFollowUpServiceImpl followUpService;

    private LeadStatusTransitionServiceImpl statusTransitionService;

    private User counselor;
    private Department dept;
    private Lead lead1;
    private Lead lead2;
    private LeadStatus rawStatus;
    private LeadStatus interestedStatus;

    @BeforeEach
    void setUp() throws UnauthorizedException, BadRequestException {
        dept = Department.builder().name("Admissions").code("ADM").active(true).build();
        dept.setId(UUID.randomUUID());

        counselor = User.builder().username("counselor1").active(true).build();
        counselor.setId(UUID.randomUUID());

        rawStatus = LeadStatus.builder().name("Raw").code("RAW").active(true).isFollowUpStatus(false).build();
        rawStatus.setId(UUID.randomUUID());

        interestedStatus = LeadStatus.builder().name("Interested").code("INTERESTED").active(true).isFollowUpStatus(true).build();
        interestedStatus.setId(UUID.randomUUID());

        lead1 = Lead.builder()
                .leadCode("LEAD-001")
                .fullName("Rahul Sharma")
                .assignedTo(counselor)
                .department(dept)
                .currentStatus(rawStatus)
                .build();
        lead1.setId(UUID.randomUUID());

        lead2 = Lead.builder()
                .leadCode("LEAD-002")
                .fullName("Priya Verma")
                .assignedTo(counselor)
                .department(dept)
                .currentStatus(rawStatus)
                .build();
        lead2.setId(UUID.randomUUID());

        when(leadRepository.findByIdForUpdate(lead1.getId())).thenReturn(Optional.of(lead1));
        when(leadRepository.findById(lead1.getId())).thenReturn(Optional.of(lead1));
        when(leadRepository.findByIdForUpdate(lead2.getId())).thenReturn(Optional.of(lead2));
        when(leadRepository.findById(lead2.getId())).thenReturn(Optional.of(lead2));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        when(userRepository.findByUsername("counselor1")).thenReturn(Optional.of(counselor));

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("counselor1");
        when(auth.getPrincipal()).thenReturn("counselor1");
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(auth);
        SecurityContextHolder.setContext(securityContext);

        UserDataScope scope = UserDataScope.builder()
                .scopeType(ScopeType.SELF)
                .userId(counselor.getId())
                .departmentIds(Collections.singleton(dept.getId()))
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);

        when(leadMapper.toDto(any(LeadFollowUp.class))).thenAnswer(inv -> {
            LeadFollowUp f = inv.getArgument(0);
            return LeadFollowUpResponse.builder()
                    .id(f.getId())
                    .leadId(f.getLead() != null ? f.getLead().getId() : null)
                    .followUpDate(f.getFollowUpDate())
                    .status(f.getStatus())
                    .completed(f.isCompleted())
                    .completedAt(f.getCompletedAt())
                    .remarks(f.getRemarks())
                    .build();
        });

        when(leadFollowUpRepository.save(any(LeadFollowUp.class))).thenAnswer(inv -> {
            LeadFollowUp f = inv.getArgument(0);
            if (f.getId() == null) f.setId(UUID.randomUUID());
            return f;
        });

        when(leadFollowUpRepository.saveAll(anyIterable())).thenAnswer(inv -> inv.getArgument(0));

        when(leadActionEnforcementService.checkActionEnforcement(any(Lead.class), any(User.class)))
                .thenReturn(com.app.datadistribution.dto.lead.LeadActionEnforcementDTO.builder().restricted(false).build());

        // Instantiate LeadStatusTransitionServiceImpl directly for transition tests
        statusTransitionService = new LeadStatusTransitionServiceImpl(
                leadRepository, leadStatusRepository, leadStatusHistoryRepository, leadFollowUpRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Phase 4: Scheduling new follow-up resolves all eligible MISSED follow-ups for same lead to MISSED_COMPLETED")
    void testSchedulingNewFollowUp_ResolvesMissedFollowUpsForSameLead() throws Exception {
        LocalDateTime originalDate = LocalDateTime.now().minusDays(3);
        LeadFollowUp missedF1 = LeadFollowUp.builder()
                .lead(lead1)
                .followUpDate(originalDate)
                .remarks("Student requested callback")
                .status(FollowUpStatus.MISSED)
                .completed(false)
                .assignedTo(counselor)
                .createdByUser(counselor)
                .build();
        missedF1.setId(UUID.randomUUID());

        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(lead1.getId())).thenReturn(false);
        when(leadFollowUpRepository.findMissedFollowUpsByLeadId(lead1.getId())).thenReturn(List.of(missedF1));

        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .leadId(lead1.getId())
                .followUpDate(LocalDateTime.now().plusDays(2))
                .remarks("Follow up scheduled after student call")
                .build();

        LeadFollowUpResponse response = followUpService.createFollowUp(lead1.getId(), request);

        assertNotNull(response);
        assertEquals(FollowUpStatus.UPCOMING, response.getStatus());

        // Verify the previous missed follow-up was updated to MISSED_COMPLETED
        assertEquals(FollowUpStatus.MISSED_COMPLETED, missedF1.getStatus());
        assertTrue(missedF1.isCompleted());
        assertNotNull(missedF1.getCompletedAt());
        // Verify original date, assignedTo, createdByUser preserved
        assertEquals(originalDate, missedF1.getFollowUpDate());
        assertEquals(counselor, missedF1.getAssignedTo());
        assertEquals(counselor, missedF1.getCreatedByUser());
        // Verify notes contain original remark + auto-resolved tag
        assertTrue(missedF1.getRemarks().contains("Student requested callback"));
        assertTrue(missedF1.getRemarks().contains("NEW_FOLLOWUP_SCHEDULED"));

        verify(leadFollowUpRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Phase 4/6: Multiple missed follow-ups on the same lead are all resolved")
    void testSchedulingNewFollowUp_ResolvesMultipleMissedFollowUps() throws Exception {
        LeadFollowUp missedF1 = LeadFollowUp.builder()
                .lead(lead1)
                .followUpDate(LocalDateTime.now().minusDays(5))
                .remarks("First missed note")
                .status(FollowUpStatus.MISSED)
                .completed(false)
                .build();
        missedF1.setId(UUID.randomUUID());

        LeadFollowUp missedF2 = LeadFollowUp.builder()
                .lead(lead1)
                .followUpDate(LocalDateTime.now().minusDays(2))
                .remarks("Second missed note")
                .status(FollowUpStatus.MISSED)
                .completed(false)
                .build();
        missedF2.setId(UUID.randomUUID());

        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(lead1.getId())).thenReturn(false);
        when(leadFollowUpRepository.findMissedFollowUpsByLeadId(lead1.getId())).thenReturn(List.of(missedF1, missedF2));

        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .leadId(lead1.getId())
                .followUpDate(LocalDateTime.now().plusDays(1))
                .remarks("New scheduled follow-up")
                .build();

        followUpService.createFollowUp(lead1.getId(), request);

        assertEquals(FollowUpStatus.MISSED_COMPLETED, missedF1.getStatus());
        assertTrue(missedF1.isCompleted());
        assertEquals(FollowUpStatus.MISSED_COMPLETED, missedF2.getStatus());
        assertTrue(missedF2.isCompleted());
    }

    @Test
    @DisplayName("Phase 4/6: Missed follow-ups belonging to another lead are NOT affected")
    void testSchedulingNewFollowUp_DoesNotAffectOtherLeads() throws Exception {
        LeadFollowUp lead2Missed = LeadFollowUp.builder()
                .lead(lead2)
                .followUpDate(LocalDateTime.now().minusDays(3))
                .remarks("Lead 2 missed note")
                .status(FollowUpStatus.MISSED)
                .completed(false)
                .build();
        lead2Missed.setId(UUID.randomUUID());

        // Lead 1 has no missed follow-ups, Lead 2 does
        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(lead1.getId())).thenReturn(false);
        when(leadFollowUpRepository.findMissedFollowUpsByLeadId(lead1.getId())).thenReturn(Collections.emptyList());

        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .leadId(lead1.getId())
                .followUpDate(LocalDateTime.now().plusDays(1))
                .remarks("Scheduling for Lead 1")
                .build();

        followUpService.createFollowUp(lead1.getId(), request);

        // Lead 2's follow-up remains MISSED
        assertEquals(FollowUpStatus.MISSED, lead2Missed.getStatus());
        assertFalse(lead2Missed.isCompleted());
        verify(leadFollowUpRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Phase 5: Changing lead status resolves eligible MISSED follow-ups to MISSED_COMPLETED")
    void testChangingLeadStatus_ResolvesMissedFollowUps() throws Exception {
        LeadFollowUp missedF = LeadFollowUp.builder()
                .lead(lead1)
                .followUpDate(LocalDateTime.now().minusDays(2))
                .remarks("Missed yesterday")
                .status(FollowUpStatus.MISSED)
                .completed(false)
                .build();
        missedF.setId(UUID.randomUUID());

        when(leadFollowUpRepository.findMissedFollowUpsByLeadId(lead1.getId())).thenReturn(List.of(missedF));

        Lead updated = statusTransitionService.executeStatusTransition(lead1, interestedStatus, counselor, "Discussed courses with student");

        assertNotNull(updated);
        assertEquals(interestedStatus, updated.getCurrentStatus());

        assertEquals(FollowUpStatus.MISSED_COMPLETED, missedF.getStatus());
        assertTrue(missedF.isCompleted());
        assertNotNull(missedF.getCompletedAt());
        assertTrue(missedF.getRemarks().contains("LEAD_STATUS_CHANGED"));
    }

    @Test
    @DisplayName("Phase 5/6: Saving same lead status again does NOT resolve follow-ups or trigger duplicate transitions")
    void testSavingSameLeadStatusAgain_DoesNotTriggerResolution() throws Exception {
        lead1.setCurrentStatus(interestedStatus);

        LeadFollowUp missedF = LeadFollowUp.builder()
                .lead(lead1)
                .followUpDate(LocalDateTime.now().minusDays(2))
                .remarks("Missed note")
                .status(FollowUpStatus.MISSED)
                .completed(false)
                .build();
        missedF.setId(UUID.randomUUID());

        when(leadFollowUpRepository.findMissedFollowUpsByLeadId(lead1.getId())).thenReturn(List.of(missedF));

        Lead result = statusTransitionService.executeStatusTransition(lead1, interestedStatus, counselor, "Same status saved");

        assertSame(lead1, result);
        // Follow-up must remain MISSED because no status transition occurred
        assertEquals(FollowUpStatus.MISSED, missedF.getStatus());
        assertFalse(missedF.isCompleted());
        verify(leadFollowUpRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Phase 6: UPCOMING and COMPLETED follow-ups remain unaffected when resolving missed follow-ups")
    void testUpcomingAndCompletedFollowUps_RemainUnaffected() {
        LeadFollowUp completedF = LeadFollowUp.builder()
                .lead(lead1)
                .followUpDate(LocalDateTime.now().minusDays(4))
                .status(FollowUpStatus.COMPLETED)
                .completed(true)
                .build();
        completedF.setId(UUID.randomUUID());

        LeadFollowUp upcomingF = LeadFollowUp.builder()
                .lead(lead1)
                .followUpDate(LocalDateTime.now().plusDays(2))
                .status(FollowUpStatus.UPCOMING)
                .completed(false)
                .build();
        upcomingF.setId(UUID.randomUUID());

        when(leadFollowUpRepository.findMissedFollowUpsByLeadId(lead1.getId())).thenReturn(Collections.emptyList());

        int resolved = followUpService.resolveMissedFollowUpsForLead(lead1.getId(), counselor, "TEST");

        assertEquals(0, resolved);
        assertEquals(FollowUpStatus.COMPLETED, completedF.getStatus());
        assertTrue(completedF.isCompleted());
        assertEquals(FollowUpStatus.UPCOMING, upcomingF.getStatus());
        assertFalse(upcomingF.isCompleted());
    }

    @Test
    @DisplayName("Phase 4/6: Cannot reschedule a MISSED_COMPLETED follow-up")
    void testReschedule_MissedCompletedFollowUp_ThrowsBadRequest() {
        LeadFollowUp missedCompleted = LeadFollowUp.builder()
                .lead(lead1)
                .followUpDate(LocalDateTime.now().minusDays(2))
                .status(FollowUpStatus.MISSED_COMPLETED)
                .completed(true)
                .build();
        missedCompleted.setId(UUID.randomUUID());

        when(leadFollowUpRepository.findById(missedCompleted.getId())).thenReturn(Optional.of(missedCompleted));

        RescheduleFollowUpRequest request = new RescheduleFollowUpRequest();
        request.setNewFollowUpDate(LocalDateTime.now().plusDays(2));
        request.setRemarks("Attempt to reschedule completed");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> followUpService.rescheduleFollowUp(missedCompleted.getId(), request));

        assertTrue(ex.getMessage().contains("Cannot reschedule"));
    }

    @Test
    @DisplayName("Phase 4/6: Directly rescheduling a MISSED follow-up updates it in-place without duplicates")
    void testReschedule_MissedFollowUpDirectly_TransitionsToUpcoming() throws Exception {
        LocalDateTime originalDate = LocalDateTime.now().minusDays(3);
        LeadFollowUp missedF = LeadFollowUp.builder()
                .lead(lead1)
                .followUpDate(originalDate)
                .status(FollowUpStatus.MISSED)
                .completed(false)
                .remarks("Missed previously")
                .build();
        missedF.setId(UUID.randomUUID());

        when(leadFollowUpRepository.findById(missedF.getId())).thenReturn(Optional.of(missedF));

        LocalDateTime newDate = LocalDateTime.now().plusDays(3);
        RescheduleFollowUpRequest request = new RescheduleFollowUpRequest();
        request.setNewFollowUpDate(newDate);
        request.setRemarks("Rescheduled by student request");

        LeadFollowUpResponse res = followUpService.rescheduleFollowUp(missedF.getId(), request);

        assertNotNull(res);
        assertEquals(FollowUpStatus.UPCOMING, missedF.getStatus());
        assertEquals(newDate, missedF.getFollowUpDate());
        assertFalse(missedF.isCompleted());
        assertTrue(missedF.getRemarks().contains("Rescheduled by student request"));
        assertTrue(missedF.getRemarks().contains("Missed previously"));
    }
}
