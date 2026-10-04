package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.lead.FollowUpOpenResponseDTO;
import com.app.datadistribution.dto.lead.LeadActionEnforcementDTO;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.impl.LeadActionEnforcementServiceImpl;
import com.app.datadistribution.service.impl.LeadFollowUpServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.ILeadStatusTransitionService;

@ExtendWith(MockitoExtension.class)
public class LeadActionEnforcementServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ILeadDataScopeService leadDataScopeService;

    @Mock
    private LeadMapper leadMapper;

    @Mock
    private ILeadStatusTransitionService leadStatusTransitionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LeadActionEnforcementServiceImpl enforcementService;

    private LeadFollowUpServiceImpl followUpService;

    private User counselor;
    private User otherCounselor;
    private User admin;
    private Lead lead;
    private LeadStatus rawStatus;
    private LeadStatus registeredStatus;
    private LeadStatus notConnectedStatus;
    private LeadStatus notConnectedSubStatus;
    private LeadStatus badStatus;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void setUp() throws Exception {
        followUpService = new LeadFollowUpServiceImpl(
                leadFollowUpRepository,
                leadRepository,
                null,
                userRepository,
                leadMapper,
                leadDataScopeService,
                leadStatusTransitionService,
                eventPublisher,
                enforcementService
        );

        Role counselorRole = Role.builder().name(RoleType.COUNSELOR.name()).build();
        Role adminRole = Role.builder().name(RoleType.ADMIN.name()).build();

        counselor = User.builder()
                .firstName("John")
                .lastName("Counselor")
                .username("counselor1")
                .roles(Set.of(counselorRole))
                .build();
        counselor.setId(UUID.randomUUID());

        otherCounselor = User.builder()
                .firstName("Jane")
                .lastName("Counselor")
                .username("counselor2")
                .roles(Set.of(counselorRole))
                .build();
        otherCounselor.setId(UUID.randomUUID());

        admin = User.builder()
                .firstName("Admin")
                .lastName("User")
                .username("admin1")
                .roles(Set.of(adminRole))
                .build();
        admin.setId(UUID.randomUUID());

        rawStatus = LeadStatus.builder().name("Raw").code("RAW").active(true).build();
        rawStatus.setId(UUID.randomUUID());

        registeredStatus = LeadStatus.builder().name("Registered").code("REGISTERED").active(true).build();
        registeredStatus.setId(UUID.randomUUID());

        notConnectedStatus = LeadStatus.builder().name("Not Connected").code("NOT_CONNECTED").active(true).build();
        notConnectedStatus.setId(UUID.randomUUID());

        notConnectedSubStatus = LeadStatus.builder()
                .name("Not Connected - 1")
                .code("NOT_CONNECTED_1")
                .parentStatus(notConnectedStatus)
                .active(true)
                .build();
        notConnectedSubStatus.setId(UUID.randomUUID());

        badStatus = LeadStatus.builder().name("Bad").code("BAD").active(true).build();
        badStatus.setId(UUID.randomUUID());

        lead = Lead.builder()
                .leadCode("LEAD-001")
                .fullName("Test Student")
                .phoneNumber("9876543210")
                .assignedTo(counselor)
                .currentStatus(rawStatus)
                .build();
        lead.setId(UUID.randomUUID());

        // Setup mock SecurityContext
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("counselor1");
        lenient().when(authentication.getPrincipal()).thenReturn("counselor1");
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        lenient().when(userRepository.findByUsername("counselor1")).thenReturn(Optional.of(counselor));
        lenient().when(leadDataScopeService.getCurrentUserScope()).thenReturn(UserDataScope.builder()
                .scopeType(UserDataScope.ScopeType.SELF)
                .userId(counselor.getId())
                .isCounsellor(true)
                .build());
    }

    @Test
    @DisplayName("Case 1: Lead marked REGISTERED -> navigation allowed (restricted = false)")
    void testCase1_LeadRegistered_NavigationAllowed() {
        lead.setCurrentStatus(registeredStatus);

        LeadActionEnforcementDTO enforcement = enforcementService.checkActionEnforcement(lead, counselor);

        assertFalse(enforcement.isRestricted());
        assertFalse(enforcement.isActionRequired());
        assertNull(enforcement.getReason());
    }

    @Test
    @DisplayName("Case 2: Lead has active FUTURE follow-up -> navigation allowed (restricted = false)")
    void testCase2_LeadWithFutureFollowUp_NavigationAllowed() {
        LocalDate tomorrow = LocalDate.now(BUSINESS_ZONE).plusDays(1);
        LeadFollowUp futureFollowUp = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(tomorrow.atTime(11, 0))
                .status(FollowUpStatus.UPCOMING)
                .completed(false)
                .assignedTo(counselor)
                .build();
        futureFollowUp.setId(UUID.randomUUID());

        when(leadFollowUpRepository.findByLeadIdOrderByFollowUpDateDesc(lead.getId())).thenReturn(List.of(futureFollowUp));

        LeadActionEnforcementDTO enforcement = enforcementService.checkActionEnforcement(lead, counselor);

        assertFalse(enforcement.isRestricted());
    }

    @Test
    @DisplayName("Case 3: Lead marked NOT_CONNECTED (and NOT_CONNECTED_1) -> navigation allowed (restricted = false)")
    void testCase3_LeadNotConnected_NavigationAllowed() {
        lead.setCurrentStatus(notConnectedStatus);

        LeadActionEnforcementDTO enforcement = enforcementService.checkActionEnforcement(lead, counselor);
        assertFalse(enforcement.isRestricted());

        lead.setCurrentStatus(notConnectedSubStatus);
        LeadActionEnforcementDTO subEnforcement = enforcementService.checkActionEnforcement(lead, counselor);
        assertFalse(subEnforcement.isRestricted());
    }

    @Test
    @DisplayName("Case 4: Lead marked BAD -> navigation allowed (restricted = false)")
    void testCase4_LeadBad_NavigationAllowed() {
        lead.setCurrentStatus(badStatus);

        LeadActionEnforcementDTO enforcement = enforcementService.checkActionEnforcement(lead, counselor);

        assertFalse(enforcement.isRestricted());
    }

    @Test
    @DisplayName("Case 5 & 6: New lead opened -> no qualifying action -> navigation restricted (restricted = true)")
    void testCase5And6_NewLeadNoAction_Restricted() {
        lead.setCurrentStatus(rawStatus);
        when(leadFollowUpRepository.findByLeadIdOrderByFollowUpDateDesc(lead.getId())).thenReturn(List.of());

        LeadActionEnforcementDTO enforcement = enforcementService.checkActionEnforcement(lead, counselor);

        assertTrue(enforcement.isRestricted());
        assertTrue(enforcement.isActionRequired());
        assertEquals("MANDATORY_LEAD_ACTION", enforcement.getReason());
        assertTrue(enforcement.getAllowedActions().contains("REGISTER"));
        assertTrue(enforcement.getAllowedActions().contains("FOLLOW_UP"));
        assertTrue(enforcement.getAllowedActions().contains("NOT_CONNECTED"));
        assertTrue(enforcement.getAllowedActions().contains("BAD"));
    }

    @Test
    @DisplayName("Case 7: Today's follow-up auto-completed upon open -> lead still requires action (restricted = true)")
    void testCase7_TodayFollowUpOpened_StillRequiresAction() throws Exception {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LeadFollowUp todayFollowUp = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(today.atTime(10, 0))
                .status(FollowUpStatus.PENDING)
                .completed(false)
                .assignedTo(counselor)
                .build();
        todayFollowUp.setId(UUID.randomUUID());

        when(leadFollowUpRepository.findById(todayFollowUp.getId())).thenReturn(Optional.of(todayFollowUp));
        when(leadFollowUpRepository.save(any(LeadFollowUp.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        // When opened, the follow-up list in enforcement check contains this completed follow-up
        when(leadFollowUpRepository.findByLeadIdOrderByFollowUpDateDesc(lead.getId()))
                .thenReturn(List.of(todayFollowUp));

        FollowUpOpenResponseDTO openResponse = followUpService.openFollowUp(todayFollowUp.getId());

        assertEquals(FollowUpStatus.COMPLETED, openResponse.getStatus());
        assertTrue(openResponse.isCompleted());
        assertNotNull(openResponse.getCompletedAt());
        // Crucial requirement 6: lead still requires action!
        assertTrue(openResponse.isLeadActionRequired());
        assertTrue(openResponse.getActionEnforcement().isRestricted());
        assertTrue(openResponse.getActionEnforcement().getMessage().contains("Please update the lead status"));
    }

    @Test
    @DisplayName("Case 8: Today's follow-up auto-completed -> lead transitioned to REGISTERED -> unlocked")
    void testCase8_TodayFollowUpCompletedThenRegistered_Unlocked() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LeadFollowUp completedToday = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(today.atTime(10, 0))
                .status(FollowUpStatus.COMPLETED)
                .completed(true)
                .completedAt(LocalDateTime.now(BUSINESS_ZONE))
                .assignedTo(counselor)
                .build();
        completedToday.setId(UUID.randomUUID());

        // Now user transitions status to REGISTERED
        lead.setCurrentStatus(registeredStatus);

        LeadActionEnforcementDTO enforcement = enforcementService.checkActionEnforcement(lead, counselor);

        assertFalse(enforcement.isRestricted());
    }

    @Test
    @DisplayName("Case 9: Today's follow-up auto-completed -> lead transitioned to NOT_CONNECTED -> unlocked")
    void testCase9_TodayFollowUpCompletedThenNotConnected_Unlocked() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LeadFollowUp completedToday = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(today.atTime(10, 0))
                .status(FollowUpStatus.COMPLETED)
                .completed(true)
                .completedAt(LocalDateTime.now(BUSINESS_ZONE))
                .assignedTo(counselor)
                .build();
        completedToday.setId(UUID.randomUUID());

        // Now user transitions status to NOT_CONNECTED
        lead.setCurrentStatus(notConnectedStatus);

        LeadActionEnforcementDTO enforcement = enforcementService.checkActionEnforcement(lead, counselor);

        assertFalse(enforcement.isRestricted());
    }

    @Test
    @DisplayName("Case 10: Future follow-up opened -> rejected (cannot auto-complete)")
    void testCase10_FutureFollowUp_CannotAutoComplete() {
        LocalDate tomorrow = LocalDate.now(BUSINESS_ZONE).plusDays(1);
        LeadFollowUp futureFollowUp = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(tomorrow.atTime(10, 0))
                .status(FollowUpStatus.UPCOMING)
                .completed(false)
                .assignedTo(counselor)
                .build();
        futureFollowUp.setId(UUID.randomUUID());

        when(leadFollowUpRepository.findById(futureFollowUp.getId())).thenReturn(Optional.of(futureFollowUp));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                followUpService.openFollowUp(futureFollowUp.getId()));

        assertTrue(ex.getMessage().contains("Future follow-ups"));
    }

    @Test
    @DisplayName("Case 11: Past follow-up (MISSED) opened -> rejected (cannot auto-complete)")
    void testCase11_PastFollowUp_CannotAutoComplete() {
        LocalDate yesterday = LocalDate.now(BUSINESS_ZONE).minusDays(1);
        LeadFollowUp pastFollowUp = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(yesterday.atTime(10, 0))
                .status(FollowUpStatus.MISSED)
                .completed(false)
                .assignedTo(counselor)
                .build();
        pastFollowUp.setId(UUID.randomUUID());

        when(leadFollowUpRepository.findById(pastFollowUp.getId())).thenReturn(Optional.of(pastFollowUp));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                followUpService.openFollowUp(pastFollowUp.getId()));

        assertTrue(ex.getMessage().contains("Past/missed follow-ups cannot be automatically completed"));
    }

    @Test
    @DisplayName("Case 12: Cancelled follow-up opened -> rejected (cannot auto-complete)")
    void testCase12_CancelledFollowUp_CannotAutoComplete() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LeadFollowUp cancelledFollowUp = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(today.atTime(10, 0))
                .status(FollowUpStatus.CANCELLED)
                .completed(false)
                .assignedTo(counselor)
                .build();
        cancelledFollowUp.setId(UUID.randomUUID());

        when(leadFollowUpRepository.findById(cancelledFollowUp.getId())).thenReturn(Optional.of(cancelledFollowUp));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                followUpService.openFollowUp(cancelledFollowUp.getId()));

        assertTrue(ex.getMessage().contains("Cancelled follow-ups cannot be completed"));
    }

    @Test
    @DisplayName("Case 13: User opens another user's lead -> not restricted")
    void testCase13_OtherUserLead_NotRestricted() {
        lead.setCurrentStatus(rawStatus);

        // otherCounselor views lead assigned to counselor
        LeadActionEnforcementDTO enforcement = enforcementService.checkActionEnforcement(lead, otherCounselor);

        assertFalse(enforcement.isRestricted());
    }

    @Test
    @DisplayName("Case 14: Admin opens lead -> admin bypass (not restricted)")
    void testCase14_AdminBypass_NotRestricted() {
        lead.setCurrentStatus(rawStatus);

        LeadActionEnforcementDTO enforcement = enforcementService.checkActionEnforcement(lead, admin);

        assertFalse(enforcement.isRestricted());
    }

    @Test
    @DisplayName("Case 17: Double-click follow-up open -> idempotent return without error")
    void testCase17_DoubleClickFollowUp_Idempotent() throws Exception {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDateTime completedAt = LocalDateTime.now(BUSINESS_ZONE);

        LeadFollowUp alreadyCompleted = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(today.atTime(10, 0))
                .status(FollowUpStatus.COMPLETED)
                .completed(true)
                .completedAt(completedAt)
                .assignedTo(counselor)
                .build();
        alreadyCompleted.setId(UUID.randomUUID());

        when(leadFollowUpRepository.findById(alreadyCompleted.getId())).thenReturn(Optional.of(alreadyCompleted));
        when(leadFollowUpRepository.findByLeadIdOrderByFollowUpDateDesc(lead.getId()))
                .thenReturn(List.of(alreadyCompleted));

        // Calling open on already completed follow-up
        FollowUpOpenResponseDTO response = followUpService.openFollowUp(alreadyCompleted.getId());

        assertEquals(FollowUpStatus.COMPLETED, response.getStatus());
        assertTrue(response.isCompleted());
        assertEquals(completedAt, response.getCompletedAt());
        verify(leadFollowUpRepository, never()).save(any(LeadFollowUp.class));
    }
}
