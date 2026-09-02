package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.dto.lead.NotConnectedFollowUpRequest;
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
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.impl.LeadFollowUpServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.ILeadStatusTransitionService;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class LeadFollowUpNotConnectedSyncTest {

    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadStatusRepository leadStatusRepository;

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

    @InjectMocks
    private LeadFollowUpServiceImpl leadFollowUpService;

    private User counselorUser;
    private User otherCounselor;
    private Lead testLead;
    private LeadFollowUp testFollowUp;
    private LeadStatus rawStatus;
    private LeadStatus notConnectedStatus;
    private UserDataScope counselorScope;

    @BeforeEach
    void setUp() {
        counselorUser = User.builder()
                .username("counselor1")
                .firstName("Abhishek")
                .lastName("Sharma")
                .active(true)
                .build();
        counselorUser.setId(UUID.randomUUID());

        otherCounselor = User.builder()
                .username("counselor2")
                .firstName("Rohan")
                .lastName("Verma")
                .active(true)
                .build();
        otherCounselor.setId(UUID.randomUUID());

        rawStatus = LeadStatus.builder()
                .name("Raw")
                .code("RAW")
                .active(true)
                .build();
        rawStatus.setId(UUID.randomUUID());

        notConnectedStatus = LeadStatus.builder()
                .name("Not Connected")
                .code("NOT_CONNECTED")
                .active(true)
                .build();
        notConnectedStatus.setId(UUID.randomUUID());

        testLead = Lead.builder()
                .leadCode("LEAD-101")
                .fullName("Rahul Kumar")
                .assignedTo(counselorUser)
                .currentStatus(rawStatus)
                .active(true)
                .build();
        testLead.setId(UUID.randomUUID());

        testFollowUp = LeadFollowUp.builder()
                .lead(testLead)
                .assignedTo(counselorUser)
                .createdByUser(counselorUser)
                .followUpDate(LocalDateTime.now().plusHours(2))
                .status(FollowUpStatus.PENDING)
                .completed(false)
                .remarks("Initial follow-up scheduled")
                .build();
        testFollowUp.setId(UUID.randomUUID());

        counselorScope = UserDataScope.builder()
                .scopeType(UserDataScope.ScopeType.SELF)
                .userId(counselorUser.getId())
                .currentUser(counselorUser)
                .isAdmin(false)
                .isHod(false)
                .build();

        // Security Context Mocking
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("counselor1");
        when(authentication.getName()).thenReturn("counselor1");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("TEST 1 (Basic Success & Lead Status Sync): Followup becomes NOT_CONNECTED and Lead transitions to NOT_CONNECTED atomically")
    void testMarkNotConnected_Success_SynchronizesLeadStatus() throws Exception {
        when(leadFollowUpRepository.findById(testFollowUp.getId())).thenReturn(Optional.of(testFollowUp));
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(counselorScope);
        when(userRepository.findByUsername("counselor1")).thenReturn(Optional.of(counselorUser));
        when(leadStatusRepository.findByCodeIgnoreCase("NOT_CONNECTED")).thenReturn(Optional.of(notConnectedStatus));
        when(leadFollowUpRepository.save(any(LeadFollowUp.class))).thenAnswer(inv -> inv.getArgument(0));

        LeadFollowUpResponse expectedDto = LeadFollowUpResponse.builder()
                .id(testFollowUp.getId())
                .status(FollowUpStatus.NOT_CONNECTED)
                .remarks("Initial follow-up scheduled | Not Connected: Student phone was ringing but unanswered")
                .build();
        when(leadMapper.toDto(any(LeadFollowUp.class))).thenReturn(expectedDto);

        NotConnectedFollowUpRequest request = NotConnectedFollowUpRequest.builder()
                .remarks("Student phone was ringing but unanswered")
                .build();

        LeadFollowUpResponse response = leadFollowUpService.markNotConnected(testFollowUp.getId(), request);

        assertNotNull(response);
        assertEquals(FollowUpStatus.NOT_CONNECTED, response.getStatus());

        // Verify Follow-up status updated
        assertEquals(FollowUpStatus.NOT_CONNECTED, testFollowUp.getStatus());
        assertFalse(testFollowUp.isCompleted());
        assertTrue(testFollowUp.getRemarks().contains("Not Connected: Student phone was ringing but unanswered"));

        // Verify Lead status transition service was called with dynamic NOT_CONNECTED status
        verify(leadStatusTransitionService, times(1))
                .executeStatusTransition(eq(testLead), eq(notConnectedStatus), eq(counselorUser), eq("Student phone was ringing but unanswered"));

        verify(leadFollowUpRepository, times(1)).save(testFollowUp);
    }

    @Test
    @DisplayName("TEST 2 (Atomic Rollback): If Lead status transition fails, entire operation throws exception")
    void testMarkNotConnected_LeadTransitionFails_RollsBack() throws Exception {
        when(leadFollowUpRepository.findById(testFollowUp.getId())).thenReturn(Optional.of(testFollowUp));
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(counselorScope);
        when(userRepository.findByUsername("counselor1")).thenReturn(Optional.of(counselorUser));
        when(leadStatusRepository.findByCodeIgnoreCase("NOT_CONNECTED")).thenReturn(Optional.of(notConnectedStatus));

        // Force leadStatusTransitionService to throw an exception
        doThrow(new BadRequestException("Maximum not-connected attempts reached for today"))
                .when(leadStatusTransitionService)
                .executeStatusTransition(any(Lead.class), any(LeadStatus.class), any(User.class), anyString());

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                leadFollowUpService.markNotConnected(testFollowUp.getId(), "Student did not answer call")
        );

        assertTrue(ex.getMessage().contains("Maximum not-connected attempts reached"));
    }

    @Test
    @DisplayName("TEST 3 (Authorization / Ownership): Unauthorized counselor cannot mark another user's followup as NOT_CONNECTED")
    void testMarkNotConnected_UnauthorizedCounselor_ThrowsException() throws Exception {
        when(leadFollowUpRepository.findById(testFollowUp.getId())).thenReturn(Optional.of(testFollowUp));
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(counselorScope);

        // Authenticated user is otherCounselor (not assigned user)
        when(userRepository.findByUsername("counselor1")).thenReturn(Optional.of(otherCounselor));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                leadFollowUpService.markNotConnected(testFollowUp.getId(), "Attempting to mark someone else's followup")
        );

        assertTrue(ex.getMessage().contains("Only the assigned user or department head"));
        verifyNoInteractions(leadStatusTransitionService);
    }

    @Test
    @DisplayName("TEST 4 (Invalid Status Transition): Cannot mark COMPLETED or CANCELLED followup as NOT_CONNECTED")
    void testMarkNotConnected_FromCompletedStatus_ThrowsBadRequest() {
        testFollowUp.setStatus(FollowUpStatus.COMPLETED);
        testFollowUp.setCompleted(true);
        when(leadFollowUpRepository.findById(testFollowUp.getId())).thenReturn(Optional.of(testFollowUp));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                leadFollowUpService.markNotConnected(testFollowUp.getId(), "Call failed")
        );

        assertTrue(ex.getMessage().contains("Only PENDING or UPCOMING follow-ups can be marked as not connected"));
        verifyNoInteractions(leadStatusTransitionService);
    }

    @Test
    @DisplayName("TEST 5 (Mandatory Remarks Validation): Blank or empty remarks is rejected")
    void testMarkNotConnected_BlankRemarks_ThrowsBadRequest() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                leadFollowUpService.markNotConnected(testFollowUp.getId(), "   ")
        );

        assertTrue(ex.getMessage().contains("Remarks/feedback is required"));
        verifyNoInteractions(leadFollowUpRepository);
        verifyNoInteractions(leadStatusTransitionService);
    }

    @Test
    @DisplayName("TEST 6 (Dynamic Status Lookup): Fails gracefully if dynamic NOT_CONNECTED status is not configured")
    void testMarkNotConnected_MissingDynamicStatus_ThrowsBadRequest() throws Exception {
        when(leadFollowUpRepository.findById(testFollowUp.getId())).thenReturn(Optional.of(testFollowUp));
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(counselorScope);
        when(userRepository.findByUsername("counselor1")).thenReturn(Optional.of(counselorUser));

        // Status NOT_CONNECTED is missing from database
        when(leadStatusRepository.findByCodeIgnoreCase("NOT_CONNECTED")).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                leadFollowUpService.markNotConnected(testFollowUp.getId(), "Student did not answer")
        );

        assertTrue(ex.getMessage().contains("Dynamic lead status 'NOT_CONNECTED' is not configured"));
        verifyNoInteractions(leadStatusTransitionService);
    }
}
