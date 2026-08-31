package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.SentimentCategory;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusHistoryRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.service.impl.LeadStatusTransitionServiceImpl;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeadStatusTransitionEngineTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private LeadStatusHistoryRepository leadStatusHistoryRepository;

    @InjectMocks
    private LeadStatusTransitionServiceImpl transitionService;

    private User counselor;
    private User admin;
    private LeadStatus rawStatus;
    private LeadStatus connectedStatus;
    private LeadStatus interestedStatus;
    private LeadStatus formFollowUpStatus;
    private LeadStatus registeredStatus;
    private LeadStatus notConnectedStatus;
    private LeadStatus notConnected1Status;
    private LeadStatus notConnected2Status;
    private LeadStatus notConnected3Status;

    private List<LeadStatusHistory> savedHistories;

    @BeforeEach
    void setUp() {
        savedHistories = new ArrayList<>();

        counselor = User.builder().username("counselor1").active(true).build();
        counselor.setId(UUID.randomUUID());

        admin = User.builder().username("admin1").active(true).build();
        admin.setId(UUID.randomUUID());

        // Build Dynamic Hierarchy Tree
        rawStatus = LeadStatus.builder().name("Raw").code("RAW").active(true).parentStatus(null).build();
        rawStatus.setId(UUID.randomUUID());

        connectedStatus = LeadStatus.builder().name("Connected").code("CONNECTED").active(true).parentStatus(rawStatus).build();
        connectedStatus.setId(UUID.randomUUID());

        interestedStatus = LeadStatus.builder().name("Interested").code("INTERESTED").active(true).parentStatus(connectedStatus).build();
        interestedStatus.setId(UUID.randomUUID());

        formFollowUpStatus = LeadStatus.builder().name("Form Follow-Up").code("FORM_FOLLOW_UP").active(true).parentStatus(interestedStatus).build();
        formFollowUpStatus.setId(UUID.randomUUID());

        registeredStatus = LeadStatus.builder().name("Registered").code("REGISTERED").active(true).parentStatus(formFollowUpStatus).build();
        registeredStatus.setId(UUID.randomUUID());

        // Sequential NOT_CONNECTED branch (daily limit = 2)
        notConnectedStatus = LeadStatus.builder().name("Not Connected").code("NOT_CONNECTED").active(true).parentStatus(rawStatus).isSequential(true).dailyAttemptLimit(2).build();
        notConnectedStatus.setId(UUID.randomUUID());

        notConnected1Status = LeadStatus.builder().name("Not Connected - 1").code("NOT_CONNECTED_1").active(true).parentStatus(notConnectedStatus).isSequential(true).dailyAttemptLimit(2).build();
        notConnected1Status.setId(UUID.randomUUID());

        notConnected2Status = LeadStatus.builder().name("Not Connected - 2").code("NOT_CONNECTED_2").active(true).parentStatus(notConnected1Status).isSequential(true).dailyAttemptLimit(2).build();
        notConnected2Status.setId(UUID.randomUUID());

        notConnected3Status = LeadStatus.builder().name("Not Connected - 3").code("NOT_CONNECTED_3").active(true).parentStatus(notConnected2Status).isSequential(true).dailyAttemptLimit(2).build();
        notConnected3Status.setId(UUID.randomUUID());

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadStatusHistoryRepository.save(any(LeadStatusHistory.class))).thenAnswer(inv -> {
            LeadStatusHistory h = inv.getArgument(0);
            if (h.getCreatedAt() == null) {
                h.setCreatedAt(LocalDateTime.now());
            }
            savedHistories.add(h);
            return h;
        });
        when(leadStatusHistoryRepository.findByLeadId(any(UUID.class))).thenAnswer(inv -> savedHistories);
    }

    @Test
    @DisplayName("Test 1: Automatic Intermediate Status Progression (RAW -> FORM_FOLLOW_UP)")
    void test1_AutoIntermediateStatusProgression() throws Exception {
        Lead lead = Lead.builder()
                .leadCode("LEAD-101")
                .currentStatus(rawStatus)
                .assignedTo(counselor)
                .statusHistories(new ArrayList<>())
                .build();
        lead.setId(UUID.randomUUID());

        Lead updated = transitionService.executeStatusTransition(lead, formFollowUpStatus, counselor, "Interested in registration");

        assertEquals(formFollowUpStatus, updated.getCurrentStatus());
        assertNotNull(updated.getLastContactedAt());

        // 3 entries: CONNECTED (AUTO_PARENT), INTERESTED (AUTO_PARENT), FORM_FOLLOW_UP (USER)
        assertEquals(3, savedHistories.size());

        assertEquals("Connected", savedHistories.get(0).getNewStatus().getName());
        assertEquals("AUTO_PARENT", savedHistories.get(0).getTransitionType());

        assertEquals("Interested", savedHistories.get(1).getNewStatus().getName());
        assertEquals("AUTO_PARENT", savedHistories.get(1).getTransitionType());

        assertEquals("Form Follow-Up", savedHistories.get(2).getNewStatus().getName());
        assertEquals("USER", savedHistories.get(2).getTransitionType());
        assertEquals("Interested in registration", savedHistories.get(2).getFeedback());
    }

    @Test
    @DisplayName("Test 2: Idempotency - Already traversed intermediate statuses are not duplicated")
    void test2_Idempotency_NoDuplicateHistories() throws Exception {
        Lead lead = Lead.builder()
                .leadCode("LEAD-102")
                .currentStatus(rawStatus)
                .assignedTo(counselor)
                .statusHistories(new ArrayList<>())
                .build();
        lead.setId(UUID.randomUUID());

        // Move to FORM_FOLLOW_UP
        transitionService.executeStatusTransition(lead, formFollowUpStatus, counselor, "Form follow up");
        assertEquals(3, savedHistories.size());

        // Now move from FORM_FOLLOW_UP to REGISTERED
        transitionService.executeStatusTransition(lead, registeredStatus, counselor, "Student completed registration");

        // Total histories should now be 4 (only 1 new entry added: REGISTERED)
        assertEquals(4, savedHistories.size());
        assertEquals("Registered", savedHistories.get(3).getNewStatus().getName());
        assertEquals("USER", savedHistories.get(3).getTransitionType());
    }

    @Test
    @DisplayName("Test 3: Sequential Progression - RAW -> NOT_CONNECTED_1 auto-inserts NOT_CONNECTED")
    void test3_Sequential_AutoInsertsParent() throws Exception {
        Lead lead = Lead.builder()
                .leadCode("LEAD-103")
                .currentStatus(rawStatus)
                .assignedTo(counselor)
                .statusHistories(new ArrayList<>())
                .build();
        lead.setId(UUID.randomUUID());

        Lead updated = transitionService.executeStatusTransition(lead, notConnected1Status, counselor, "Ringing, no response");

        assertEquals(notConnected1Status, updated.getCurrentStatus());
        assertEquals(2, savedHistories.size());
        assertEquals("Not Connected", savedHistories.get(0).getNewStatus().getName());
        assertEquals("AUTO_PARENT", savedHistories.get(0).getTransitionType());
        assertEquals("Not Connected - 1", savedHistories.get(1).getNewStatus().getName());
        assertEquals("USER", savedHistories.get(1).getTransitionType());
    }

    @Test
    @DisplayName("Test 4: Sequential Progression - Next step NOT_CONNECTED_1 -> NOT_CONNECTED_2 is allowed")
    void test4_Sequential_StepByStepAllowed() throws Exception {
        Lead lead = Lead.builder()
                .leadCode("LEAD-104")
                .currentStatus(notConnected1Status)
                .assignedTo(counselor)
                .statusHistories(new ArrayList<>())
                .build();
        lead.setId(UUID.randomUUID());

        Lead updated = transitionService.executeStatusTransition(lead, notConnected2Status, counselor, "Second attempt failed");

        assertEquals(notConnected2Status, updated.getCurrentStatus());
        assertEquals(1, savedHistories.size());
        assertEquals("Not Connected - 2", savedHistories.get(0).getNewStatus().getName());
    }

    @Test
    @DisplayName("Test 5: Sequential Progression - Skipping steps (NOT_CONNECTED_1 -> NOT_CONNECTED_3) is blocked")
    void test5_Sequential_SkippingStepBlocked() {
        Lead lead = Lead.builder()
                .leadCode("LEAD-105")
                .currentStatus(notConnected1Status)
                .assignedTo(counselor)
                .statusHistories(new ArrayList<>())
                .build();
        lead.setId(UUID.randomUUID());

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                transitionService.executeStatusTransition(lead, notConnected3Status, counselor, "Skipping to attempt 3"));

        assertTrue(ex.getMessage().contains("Sequential progression required"));
    }

    @Test
    @DisplayName("Test 6: Daily Rate Limit - 3rd NOT_CONNECTED attempt on same calendar day is blocked")
    void test6_DailyRateLimit_ThirdAttemptBlocked() throws Exception {
        Lead lead = Lead.builder()
                .leadCode("LEAD-106")
                .currentStatus(rawStatus)
                .assignedTo(counselor)
                .statusHistories(new ArrayList<>())
                .build();
        lead.setId(UUID.randomUUID());

        // 1st attempt today
        transitionService.executeStatusTransition(lead, notConnected1Status, counselor, "Attempt 1");
        assertEquals(notConnected1Status, lead.getCurrentStatus());

        // 2nd attempt today
        transitionService.executeStatusTransition(lead, notConnected2Status, counselor, "Attempt 2");
        assertEquals(notConnected2Status, lead.getCurrentStatus());

        // 3rd attempt today -> MUST BE BLOCKED
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                transitionService.executeStatusTransition(lead, notConnected3Status, counselor, "Attempt 3"));

        assertTrue(ex.getMessage().contains("Maximum 2 not-connected attempts are allowed"));
    }

    @Test
    @DisplayName("Test 7: Admin Override - Non-assigned user direct update without counselor sequential restriction")
    void test7_AdminDirectOverride() throws Exception {
        Lead lead = Lead.builder()
                .leadCode("LEAD-107")
                .currentStatus(rawStatus)
                .assignedTo(counselor)
                .statusHistories(new ArrayList<>())
                .build();
        lead.setId(UUID.randomUUID());

        Lead updated = transitionService.executeStatusTransition(lead, registeredStatus, admin, "Admin direct registration");

        assertEquals(registeredStatus, updated.getCurrentStatus());
        assertEquals(1, savedHistories.size());
        assertEquals("Registered", savedHistories.get(0).getNewStatus().getName());
        assertEquals("ADMIN_OVERRIDE", savedHistories.get(0).getTransitionType());
    }

    @Test
    @DisplayName("Test 8: Dynamic Custom Hierarchy - Algorithm works generically for arbitrary trees")
    void test8_DynamicCustomHierarchy() throws Exception {
        LeadStatus customRoot = LeadStatus.builder().name("Level 0").code("L0").active(true).build();
        customRoot.setId(UUID.randomUUID());

        LeadStatus customL1 = LeadStatus.builder().name("Level 1").code("L1").active(true).parentStatus(customRoot).build();
        customL1.setId(UUID.randomUUID());

        LeadStatus customL2 = LeadStatus.builder().name("Level 2").code("L2").active(true).parentStatus(customL1).build();
        customL2.setId(UUID.randomUUID());

        Lead customLead = Lead.builder()
                .leadCode("CUSTOM-001")
                .currentStatus(customRoot)
                .assignedTo(counselor)
                .statusHistories(new ArrayList<>())
                .build();
        customLead.setId(UUID.randomUUID());

        Lead updated = transitionService.executeStatusTransition(customLead, customL2, counselor, "Transitioning down custom tree");

        assertEquals(customL2, updated.getCurrentStatus());
        assertEquals(2, savedHistories.size());
        assertEquals("Level 1", savedHistories.get(0).getNewStatus().getName());
        assertEquals("AUTO_PARENT", savedHistories.get(0).getTransitionType());
        assertEquals("Level 2", savedHistories.get(1).getNewStatus().getName());
        assertEquals("USER", savedHistories.get(1).getTransitionType());
    }
}
