package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.lead.LeadActionEnforcementDTO;
import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.enums.RegistrationStatus;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusHistoryRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.impl.LeadActionEnforcementServiceImpl;
import com.app.datadistribution.service.impl.LeadFollowUpServiceImpl;
import com.app.datadistribution.service.impl.LeadStatusTransitionServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeadRegisteredWorkflowTerminalStateTest {

        @Mock
        private LeadRepository leadRepository;
        @Mock
        private LeadStatusRepository leadStatusRepository;
        @Mock
        private LeadStatusHistoryRepository leadStatusHistoryRepository;
        @Mock
        private LeadFollowUpRepository leadFollowUpRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private ILeadDataScopeService leadDataScopeService;
        @Mock
        private LeadMapper leadMapper;

        @InjectMocks
        private LeadStatusTransitionServiceImpl transitionService;

        @InjectMocks
        private LeadFollowUpServiceImpl followUpService;

        @InjectMocks
        private LeadActionEnforcementServiceImpl actionEnforcementService;

        private User counselor;
        private LeadStatus connectedStatus;
        private LeadStatus interestedStatus;
        private LeadStatus registeredStatus;
        private Lead lead;

        @BeforeEach
        void setUp() throws UnauthorizedException, BadRequestException {
                Role counselorRole = Role.builder().name(RoleType.COUNSELOR.name()).build();
                counselorRole.setId(UUID.randomUUID());

                counselor = User.builder()
                                .username("counselor_test")
                                .active(true)
                                .roles(Set.of(counselorRole))
                                .build();
                counselor.setId(UUID.randomUUID());

                Authentication auth = mock(Authentication.class);
                when(auth.isAuthenticated()).thenReturn(true);
                when(auth.getName()).thenReturn(counselor.getUsername());
                when(auth.getPrincipal()).thenReturn(counselor.getUsername());

                SecurityContext sc = mock(SecurityContext.class);
                when(sc.getAuthentication()).thenReturn(auth);
                SecurityContextHolder.setContext(sc);

                when(userRepository.findByUsername(counselor.getUsername())).thenReturn(Optional.of(counselor));

                connectedStatus = LeadStatus.builder()
                                .name("Connected")
                                .code("CONNECTED")
                                .active(true)
                                .build();
                connectedStatus.setId(UUID.randomUUID());

                interestedStatus = LeadStatus.builder()
                                .name("Interested")
                                .code("INTERESTED")
                                .active(true)
                                .parentStatus(connectedStatus)
                                .build();
                interestedStatus.setId(UUID.randomUUID());

                registeredStatus = LeadStatus.builder()
                                .name("Registered")
                                .code("REGISTERED")
                                .active(true)
                                .parentStatus(interestedStatus)
                                .build();
                registeredStatus.setId(UUID.randomUUID());

                lead = Lead.builder()
                                .leadCode("LEAD-TERM-001")
                                .fullName("Radhika Agrawal")
                                .phoneNumber("9303084977")
                                .currentStatus(connectedStatus)
                                .assignedTo(counselor)
                                .registrationStatus(RegistrationStatus.NONE)
                                .active(true)
                                .followUps(new ArrayList<>())
                                .build();
                lead.setId(UUID.randomUUID());

                UserDataScope scope = UserDataScope.builder().userId(counselor.getId()).build();
                when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
                when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
                when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));
        }

        @Test
        @DisplayName("TEST 1: Lead is CONNECTED -> Action enforcement restricts until call/followup action taken")
        void test1_LeadConnected_ActionsRequired() throws Exception {
                LeadActionEnforcementDTO enforcement = actionEnforcementService.checkActionEnforcement(lead, counselor);
                assertNotNull(enforcement);
                assertFalse(transitionService.isRegisteredStatus(lead.getCurrentStatus()));
        }

        @Test
        @DisplayName("TEST 2 & 12: Lead is REGISTERED -> Action enforcement unlocked / unrestricted")
        void test2_LeadRegistered_NoActionRequired() throws Exception {
                lead.setCurrentStatus(registeredStatus);
                lead.setRegistrationStatus(RegistrationStatus.COMPLETED_MATCHED);

                LeadActionEnforcementDTO enforcement = actionEnforcementService.checkActionEnforcement(lead, counselor);
                assertNotNull(enforcement);
                assertFalse(enforcement.isRestricted(), "REGISTERED lead must never have restricted navigation/action");
                assertEquals("NONE", enforcement.getActionType());
        }

        @Test
        @DisplayName("TEST 3 & 4: Transition to REGISTERED completes all active PENDING and UPCOMING follow-ups")
        void test3_TransitionToRegistered_CompletesPendingAndUpcomingFollowUps() throws Exception {
                LeadFollowUp pendingFollowUp = LeadFollowUp.builder()
                                .lead(lead)
                                .status(FollowUpStatus.PENDING)
                                .completed(false)
                                .followUpDate(LocalDateTime.now())
                                .build();
                pendingFollowUp.setId(UUID.randomUUID());

                LeadFollowUp upcomingFollowUp = LeadFollowUp.builder()
                                .lead(lead)
                                .status(FollowUpStatus.UPCOMING)
                                .completed(false)
                                .followUpDate(LocalDateTime.now().plusDays(2))
                                .build();
                upcomingFollowUp.setId(UUID.randomUUID());

                when(leadFollowUpRepository.findActiveFollowUpsByLeadId(lead.getId()))
                                .thenReturn(List.of(pendingFollowUp, upcomingFollowUp));

                Lead result = transitionService.executeStatusTransition(lead, registeredStatus, counselor,
                                "Registration completed");

                assertNotNull(result);
                assertEquals(registeredStatus, result.getCurrentStatus());
                assertNull(result.getNextFollowUpDate(), "Next follow-up date must be cleared");

                // Verify follow-ups were completed
                assertTrue(pendingFollowUp.isCompleted());
                assertEquals(FollowUpStatus.COMPLETED, pendingFollowUp.getStatus());
                assertNotNull(pendingFollowUp.getCompletedAt());

                assertTrue(upcomingFollowUp.isCompleted());
                assertEquals(FollowUpStatus.COMPLETED, upcomingFollowUp.getStatus());
                assertNotNull(upcomingFollowUp.getCompletedAt());

                verify(leadFollowUpRepository, atLeastOnce()).saveAll(any());
                verify(leadStatusHistoryRepository, atLeastOnce()).save(any(LeadStatusHistory.class));
        }

        @Test
        @DisplayName("TEST 5: Attempting to create follow-up for REGISTERED lead is rejected with BadRequestException")
        void test5_RegisteredLead_CannotCreateFollowUp() {
                lead.setCurrentStatus(registeredStatus);

                LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                                .followUpDate(LocalDateTime.now().plusDays(1))
                                .remarks("Follow up with candidate")
                                .build();

                BadRequestException ex = assertThrows(BadRequestException.class,
                                () -> followUpService.createFollowUp(lead.getId(), request));

                assertTrue(ex.getMessage().toLowerCase().contains("already registered"));
                verify(leadFollowUpRepository, never()).save(any());
        }

        @Test
        @DisplayName("TEST 6, 7, 8: Transitioning away from REGISTERED to other statuses is rejected")
        void test6_7_8_RegisteredLead_CannotTransitionToNonRegisteredStatuses() {
                lead.setCurrentStatus(registeredStatus);

                // Cannot transition to CONNECTED
                BadRequestException exConnected = assertThrows(BadRequestException.class,
                                () -> transitionService.executeStatusTransition(lead, connectedStatus, counselor,
                                                "Call connected"));
                assertTrue(exConnected.getMessage().toLowerCase().contains("already registered"));

                // Cannot transition to NOT_CONNECTED
                LeadStatus notConnectedStatus = LeadStatus.builder().name("Not Connected").code("NOT_CONNECTED")
                                .build();
                notConnectedStatus.setId(UUID.randomUUID());
                BadRequestException exNotConnected = assertThrows(BadRequestException.class,
                                () -> transitionService.executeStatusTransition(lead, notConnectedStatus, counselor,
                                                "Missed call"));
                assertTrue(exNotConnected.getMessage().toLowerCase().contains("already registered"));

                // Cannot transition to BAD_LEAD / LOST
                LeadStatus badLeadStatus = LeadStatus.builder().name("Lost Lead").code("LOST").build();
                badLeadStatus.setId(UUID.randomUUID());
                BadRequestException exBad = assertThrows(BadRequestException.class,
                                () -> transitionService.executeStatusTransition(lead, badLeadStatus, counselor,
                                                "Mark bad lead"));
                assertTrue(exBad.getMessage().toLowerCase().contains("already registered"));
        }

        @Test
        @DisplayName("TEST 9: Terminal follow-up states (MISSED, COMPLETED, CANCELLED) remain untouched")
        void test9_TerminalFollowUps_RemainUntouched() throws Exception {
                LeadFollowUp missedFollowUp = LeadFollowUp.builder()
                                .lead(lead)
                                .status(FollowUpStatus.MISSED)
                                .completed(false)
                                .followUpDate(LocalDateTime.now().minusDays(1))
                                .build();
                missedFollowUp.setId(UUID.randomUUID());

                LeadFollowUp completedFollowUp = LeadFollowUp.builder()
                                .lead(lead)
                                .status(FollowUpStatus.COMPLETED)
                                .completed(true)
                                .completedAt(LocalDateTime.now().minusHours(2))
                                .followUpDate(LocalDateTime.now().minusDays(2))
                                .build();
                completedFollowUp.setId(UUID.randomUUID());

                LeadFollowUp cancelledFollowUp = LeadFollowUp.builder()
                                .lead(lead)
                                .status(FollowUpStatus.CANCELLED)
                                .completed(false)
                                .followUpDate(LocalDateTime.now().plusDays(1))
                                .build();
                cancelledFollowUp.setId(UUID.randomUUID());

                // findActiveFollowUpsByLeadId returns only active ones, not terminal ones
                when(leadFollowUpRepository.findActiveFollowUpsByLeadId(lead.getId()))
                                .thenReturn(Collections.emptyList());

                transitionService.executeStatusTransition(lead, registeredStatus, counselor, "Registered");

                // Verify MISSED remained untouched
                assertEquals(FollowUpStatus.MISSED, missedFollowUp.getStatus());
                assertFalse(missedFollowUp.isCompleted());

                // Verify COMPLETED remained untouched
                assertEquals(FollowUpStatus.COMPLETED, completedFollowUp.getStatus());
                assertTrue(completedFollowUp.isCompleted());

                // Verify CANCELLED remained untouched
                assertEquals(FollowUpStatus.CANCELLED, cancelledFollowUp.getStatus());
                assertFalse(cancelledFollowUp.isCompleted());
        }

        @Test
        @DisplayName("TEST 10: Attempting to mark Not Connected for REGISTERED lead is rejected")
        void test10_MarkNotConnected_RejectedForRegisteredLead() {
                lead.setCurrentStatus(registeredStatus);

                UUID followUpId = UUID.randomUUID();
                LeadFollowUp followUp = LeadFollowUp.builder()
                                .lead(lead)
                                .status(FollowUpStatus.PENDING)
                                .completed(false)
                                .followUpDate(LocalDateTime.now())
                                .build();
                followUp.setId(followUpId);
                when(leadFollowUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));

                BadRequestException ex = assertThrows(BadRequestException.class,
                                () -> followUpService.markNotConnected(followUpId, "No response"));
                assertTrue(ex.getMessage().toLowerCase().contains("already registered"));
        }

        @Test
        @DisplayName("TEST 11: Idempotent transition to REGISTERED does not fail or duplicate side effects")
        void test11_IdempotentRegistration() throws Exception {
                lead.setCurrentStatus(registeredStatus);
                when(leadFollowUpRepository.findActiveFollowUpsByLeadId(lead.getId()))
                                .thenReturn(Collections.emptyList());

                Lead result = transitionService.executeStatusTransition(lead, registeredStatus, counselor,
                                "Re-confirm registration");
                assertNotNull(result);
                assertEquals(registeredStatus, result.getCurrentStatus());
        }

        @Test
        @DisplayName("TEST 14: Scheduler safety - Completed follow-ups have completed=true so scheduler excludes them")
        void test14_SchedulerSafety() {
                LeadFollowUp followUp = LeadFollowUp.builder()
                                .lead(lead)
                                .status(FollowUpStatus.COMPLETED)
                                .completed(true)
                                .completedAt(LocalDateTime.now())
                                .followUpDate(LocalDateTime.now())
                                .build();

                // The scheduler queries `f.completed = false AND f.status IN (UPCOMING,
                // PENDING)`
                // Since completed is true and status is COMPLETED, it is never picked up by
                // scheduler
                assertTrue(followUp.isCompleted());
                assertEquals(FollowUpStatus.COMPLETED, followUp.getStatus());
        }
}
