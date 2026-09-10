package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.lead.LeadDistributionRequest;
import com.app.datadistribution.dto.lead.LeadDistributionResponse;
import com.app.datadistribution.dto.lead.UserDistributionSummaryDTO;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAssignmentHistory;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.LeadAssignmentHistoryRepository;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.engine.LeadDistributionEngine;
import com.app.datadistribution.service.impl.LeadDistributionServiceImpl;

@ExtendWith(MockitoExtension.class)
class LeadDistributionTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;
    @Mock
    private LeadAssignmentHistoryRepository leadAssignmentHistoryRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private LeadDistributionEngine leadDistributionEngine;
    private LeadDistributionServiceImpl leadDistributionService;

    private User adminUser;
    private User user1;
    private User user2;
    private User user3;
    private LeadStatus rawStatus;
    private UUID rawStatusId;

    @BeforeEach
    void setUp() {
        leadDistributionEngine = new LeadDistributionEngine();
        leadDistributionService = new LeadDistributionServiceImpl(
                leadRepository,
                userRepository,
                leadFollowUpRepository,
                leadAssignmentHistoryRepository,
                leadStatusRepository,
                leadDistributionEngine,
                eventPublisher
        );

        adminUser = User.builder().username("admin").active(true).build();
        adminUser.setId(UUID.randomUUID());

        user1 = User.builder().username("counselor1").firstName("Counselor").lastName("One").email("c1@test.com").active(true).build();
        user1.setId(UUID.randomUUID());

        user2 = User.builder().username("counselor2").firstName("Counselor").lastName("Two").email("c2@test.com").active(true).build();
        user2.setId(UUID.randomUUID());

        user3 = User.builder().username("counselor3").firstName("Counselor").lastName("Three").email("c3@test.com").active(true).build();
        user3.setId(UUID.randomUUID());

        rawStatusId = UUID.randomUUID();
        rawStatus = LeadStatus.builder().name("RAW").code("RAW").active(true).build();
        rawStatus.setId(rawStatusId);

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("admin");
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        lenient().when(leadStatusRepository.findByCodeIgnoreCase("RAW")).thenReturn(Optional.of(rawStatus));
    }

    private List<Lead> createMockLeads(int count) {
        List<Lead> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Lead lead = Lead.builder()
                    .leadCode("LEAD-" + String.format("%03d", i))
                    .fullName("Student " + i)
                    .currentStatus(rawStatus)
                    .build();
            lead.setId(UUID.randomUUID());
            list.add(lead);
        }
        return list;
    }

    @Test
    void testFairRoundRobin_TenLeadsAcrossThreeEligibleUsers() throws BadRequestException, UnauthorizedException {
        List<Lead> leads = createMockLeads(10);
        List<UUID> leadIds = leads.stream().map(Lead::getId).toList();
        List<UUID> userIds = List.of(user1.getId(), user2.getId(), user3.getId());

        when(leadRepository.findAllById(leadIds)).thenReturn(leads);
        when(userRepository.findAllById(userIds)).thenReturn(List.of(user1, user2, user3));

        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId)))
                .thenReturn(Collections.emptyList());

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .leadIds(leadIds)
                .userIds(userIds)
                .build();

        LeadDistributionResponse preview = leadDistributionService.previewDistribution(request);

        assertNotNull(preview);
        assertTrue(preview.isPreviewOnly());
        assertEquals(10, preview.getTotalSelectedLeads());
        assertEquals(10, preview.getTotalAssigned());
        assertEquals(0, preview.getTotalUnassigned());

        UserDistributionSummaryDTO u1Summary = preview.getUsers().stream().filter(u -> u.getUserId().equals(user1.getId())).findFirst().orElseThrow();
        UserDistributionSummaryDTO u2Summary = preview.getUsers().stream().filter(u -> u.getUserId().equals(user2.getId())).findFirst().orElseThrow();
        UserDistributionSummaryDTO u3Summary = preview.getUsers().stream().filter(u -> u.getUserId().equals(user3.getId())).findFirst().orElseThrow();

        assertEquals(4, u1Summary.getAssignedCount());
        assertEquals(3, u2Summary.getAssignedCount());
        assertEquals(3, u3Summary.getAssignedCount());
    }

    @Test
    void testFairRoundRobin_TenLeadsAcrossTenEligibleUsers_OneLeadPerUser() throws BadRequestException, UnauthorizedException {
        List<Lead> leads = createMockLeads(10);
        List<UUID> leadIds = leads.stream().map(Lead::getId).toList();

        List<User> tenUsers = new ArrayList<>();
        List<UUID> tenUserIds = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            User u = User.builder().username("counselor" + i).firstName("Counselor").lastName(String.valueOf(i)).active(true).build();
            u.setId(UUID.randomUUID());
            tenUsers.add(u);
            tenUserIds.add(u.getId());
        }

        when(leadRepository.findAllById(leadIds)).thenReturn(leads);
        when(userRepository.findAllById(tenUserIds)).thenReturn(tenUsers);
        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId)))
                .thenReturn(Collections.emptyList());

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .leadIds(leadIds)
                .userIds(tenUserIds)
                .build();

        LeadDistributionResponse preview = leadDistributionService.previewDistribution(request);

        assertNotNull(preview);
        assertEquals(10, preview.getTotalAssigned());
        for (UserDistributionSummaryDTO summary : preview.getUsers()) {
            assertEquals(1, summary.getAssignedCount(), "Each of the 10 users must receive exactly 1 lead");
        }
    }

    @Test
    void testStrictWorkloadCapacityLimits_FollowupsAndRawLimits() throws BadRequestException, UnauthorizedException {
        List<Lead> leads = createMockLeads(8);
        List<UUID> leadIds = leads.stream().map(Lead::getId).toList();
        List<UUID> userIds = List.of(user1.getId(), user2.getId(), user3.getId());

        when(leadRepository.findAllById(leadIds)).thenReturn(leads);
        when(userRepository.findAllById(userIds)).thenReturn(List.of(user1, user2, user3));

        List<Object[]> followUpRows = new ArrayList<>();
        followUpRows.add(new Object[]{user1.getId(), 30L});
        followUpRows.add(new Object[]{user2.getId(), 10L});
        followUpRows.add(new Object[]{user3.getId(), 20L});
        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any())).thenReturn(followUpRows);

        List<Object[]> rawRows = new ArrayList<>();
        rawRows.add(new Object[]{user1.getId(), 10L});
        rawRows.add(new Object[]{user2.getId(), 40L});
        rawRows.add(new Object[]{user3.getId(), 35L});
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId))).thenReturn(rawRows);

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .leadIds(leadIds)
                .userIds(userIds)
                .build();

        LeadDistributionResponse response = leadDistributionService.previewDistribution(request);

        assertNotNull(response);
        assertEquals(8, response.getTotalSelectedLeads());
        assertEquals(5, response.getTotalAssigned());
        assertEquals(3, response.getTotalUnassigned());

        UserDistributionSummaryDTO u1 = response.getUsers().stream().filter(u -> u.getUserId().equals(user1.getId())).findFirst().orElseThrow();
        assertEquals("EXCEEDED_FOLLOWUP_LIMIT", u1.getStatus());
        assertEquals(0, u1.getAssignedCount());
        assertEquals(0, u1.getFinalCapacity());

        UserDistributionSummaryDTO u2 = response.getUsers().stream().filter(u -> u.getUserId().equals(user2.getId())).findFirst().orElseThrow();
        assertEquals("EXCEEDED_RAW_LIMIT", u2.getStatus());
        assertEquals(0, u2.getAssignedCount());
        assertEquals(0, u2.getFinalCapacity());

        UserDistributionSummaryDTO u3 = response.getUsers().stream().filter(u -> u.getUserId().equals(user3.getId())).findFirst().orElseThrow();
        assertEquals("ELIGIBLE", u3.getStatus());
        assertEquals(5, u3.getAssignedCount());
        assertEquals(5, u3.getFinalCapacity());

        assertEquals(3, response.getUnassignedLeads().size());
    }

    @Test
    void testCapacityLimits_ExceededBothLimitsStatus() throws BadRequestException, UnauthorizedException {
        List<Lead> leads = createMockLeads(2);
        List<UUID> leadIds = leads.stream().map(Lead::getId).toList();
        List<UUID> userIds = List.of(user1.getId());

        when(leadRepository.findAllById(leadIds)).thenReturn(leads);
        when(userRepository.findAllById(userIds)).thenReturn(List.of(user1));

        List<Object[]> followUpRows = new ArrayList<>();
        followUpRows.add(new Object[]{user1.getId(), 35L});
        List<Object[]> rawRows = new ArrayList<>();
        rawRows.add(new Object[]{user1.getId(), 45L});

        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any())).thenReturn(followUpRows);
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId))).thenReturn(rawRows);

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .leadIds(leadIds)
                .userIds(userIds)
                .build();

        LeadDistributionResponse response = leadDistributionService.previewDistribution(request);

        assertNotNull(response);
        assertEquals(0, response.getTotalAssigned());
        assertEquals(2, response.getTotalUnassigned());

        UserDistributionSummaryDTO u1 = response.getUsers().get(0);
        assertEquals("EXCEEDED_BOTH_LIMITS", u1.getStatus());
    }

    @Test
    void testDistributeLeads_ExplicitSelectedLeads_PersistsAssignmentAndHistory() throws BadRequestException, UnauthorizedException {
        List<Lead> leads = createMockLeads(3);
        leads.get(0).setAssignedTo(user1);

        List<UUID> leadIds = leads.stream().map(Lead::getId).toList();
        List<UUID> userIds = List.of(user2.getId());

        when(leadRepository.findAllById(leadIds)).thenReturn(leads);
        when(userRepository.findAllById(userIds)).thenReturn(List.of(user2));
        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any())).thenReturn(Collections.emptyList());
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId))).thenReturn(Collections.emptyList());
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .leadIds(leadIds)
                .userIds(userIds)
                .build();

        LeadDistributionResponse response = leadDistributionService.distributeLeads(request);

        assertNotNull(response);
        assertFalse(response.isPreviewOnly());
        assertEquals(3, response.getTotalAssigned());

        verify(leadRepository, times(3)).save(any(Lead.class));
        verify(leadAssignmentHistoryRepository, times(3)).save(any(LeadAssignmentHistory.class));
        assertEquals(user2, leads.get(0).getAssignedTo());
        assertEquals(user2, leads.get(1).getAssignedTo());
        assertEquals(user2, leads.get(2).getAssignedTo());
    }

    @Test
    void testDistributeLeads_MaximumNumberLimit_LimitsAllocations() throws BadRequestException, UnauthorizedException {
        List<Lead> leads = createMockLeads(10);
        List<UUID> leadIds = leads.stream().map(Lead::getId).toList();
        List<UUID> userIds = List.of(user1.getId(), user2.getId());

        when(leadRepository.findAllById(leadIds)).thenReturn(leads);
        when(userRepository.findAllById(userIds)).thenReturn(List.of(user1, user2));
        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any())).thenReturn(Collections.emptyList());
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId))).thenReturn(Collections.emptyList());

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .leadIds(leadIds)
                .userIds(userIds)
                .maximumNumber(4)
                .build();

        LeadDistributionResponse response = leadDistributionService.previewDistribution(request);

        assertNotNull(response);
        assertEquals(10, response.getTotalSelectedLeads());
        assertEquals(4, response.getTotalDistributableLeads());
        assertEquals(4, response.getTotalAssigned());
        assertEquals(6, response.getTotalUnassigned());
    }

    @Test
    void testAcceptanceCriteria1_TwoSelectedFromTwenty_MaxTwo_ExactlyTwoDistributed() throws BadRequestException, UnauthorizedException {
        List<Lead> allTwentyLeads = createMockLeads(20);
        Lead lead3 = allTwentyLeads.get(2);
        Lead lead15 = allTwentyLeads.get(14);
        List<Lead> selectedLeads = List.of(lead3, lead15);
        List<UUID> selectedLeadIds = List.of(lead3.getId(), lead15.getId());
        List<UUID> targetUserIds = List.of(user1.getId(), user2.getId());

        when(leadRepository.findAllById(selectedLeadIds)).thenReturn(selectedLeads);
        when(userRepository.findAllById(targetUserIds)).thenReturn(List.of(user1, user2));
        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any())).thenReturn(Collections.emptyList());
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId))).thenReturn(Collections.emptyList());
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        LeadDistributionRequest previewReq = LeadDistributionRequest.builder()
                .leadIds(selectedLeadIds)
                .userIds(targetUserIds)
                .maximumNumber(2)
                .build();

        LeadDistributionResponse previewRes = leadDistributionService.previewDistribution(previewReq);
        assertEquals(2, previewRes.getTotalSelectedLeads(), "Candidate count must be strictly 2, not 20");
        assertEquals(2, previewRes.getTotalDistributableLeads());
        assertEquals(2, previewRes.getTotalAssigned());
        assertEquals(0, previewRes.getTotalUnassigned());

        LeadDistributionResponse distRes = leadDistributionService.distributeLeads(previewReq);
        assertEquals(2, distRes.getTotalAssigned());
        verify(leadRepository, times(2)).save(any(Lead.class));
        verify(leadAssignmentHistoryRepository, times(2)).save(any(LeadAssignmentHistory.class));
        assertEquals(user1, lead3.getAssignedTo());
        assertEquals(user2, lead15.getAssignedTo());
    }

    @Test
    void testAcceptanceCriteria2_TwoSelectedFromTwenty_MaxTen_CandidateCountRemainsTwo() throws BadRequestException, UnauthorizedException {
        List<Lead> allTwentyLeads = createMockLeads(20);
        Lead lead3 = allTwentyLeads.get(2);
        Lead lead15 = allTwentyLeads.get(14);
        List<Lead> selectedLeads = List.of(lead3, lead15);
        List<UUID> selectedLeadIds = List.of(lead3.getId(), lead15.getId());
        List<UUID> targetUserIds = List.of(user1.getId(), user2.getId());

        when(leadRepository.findAllById(selectedLeadIds)).thenReturn(selectedLeads);
        when(userRepository.findAllById(targetUserIds)).thenReturn(List.of(user1, user2));
        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any())).thenReturn(Collections.emptyList());
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId))).thenReturn(Collections.emptyList());

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .leadIds(selectedLeadIds)
                .userIds(targetUserIds)
                .maximumNumber(10)
                .build();

        LeadDistributionResponse response = leadDistributionService.previewDistribution(request);
        assertEquals(2, response.getTotalSelectedLeads(), "Must never expand to 10 or 20 when only 2 selected");
        assertEquals(2, response.getTotalDistributableLeads());
        assertEquals(2, response.getTotalAssigned());
        assertEquals(0, response.getTotalUnassigned());
    }

    @Test
    void testAcceptanceCriteria3_TwoSelected_MaxOne_OnlyOneDistributedSecondUnassigned() throws BadRequestException, UnauthorizedException {
        List<Lead> selectedLeads = createMockLeads(2);
        List<UUID> selectedLeadIds = selectedLeads.stream().map(Lead::getId).toList();
        List<UUID> targetUserIds = List.of(user1.getId(), user2.getId());

        when(leadRepository.findAllById(selectedLeadIds)).thenReturn(selectedLeads);
        when(userRepository.findAllById(targetUserIds)).thenReturn(List.of(user1, user2));
        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any())).thenReturn(Collections.emptyList());
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId))).thenReturn(Collections.emptyList());

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .leadIds(selectedLeadIds)
                .userIds(targetUserIds)
                .maximumNumber(1)
                .build();

        LeadDistributionResponse response = leadDistributionService.previewDistribution(request);
        assertEquals(2, response.getTotalSelectedLeads());
        assertEquals(1, response.getTotalDistributableLeads());
        assertEquals(1, response.getTotalAssigned());
        assertEquals(1, response.getTotalUnassigned());
        assertTrue(response.getUnassignedLeads().get(0).getReason().contains("maximum number limit"));
    }

    @Test
    void testAcceptanceCriteria5_TenSelected_TwoEligibleUsers_FiveEach() throws BadRequestException, UnauthorizedException {
        List<Lead> selectedLeads = createMockLeads(10);
        List<UUID> selectedLeadIds = selectedLeads.stream().map(Lead::getId).toList();
        List<UUID> targetUserIds = List.of(user1.getId(), user2.getId());

        when(leadRepository.findAllById(selectedLeadIds)).thenReturn(selectedLeads);
        when(userRepository.findAllById(targetUserIds)).thenReturn(List.of(user1, user2));
        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any())).thenReturn(Collections.emptyList());
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId))).thenReturn(Collections.emptyList());

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .leadIds(selectedLeadIds)
                .userIds(targetUserIds)
                .maximumNumber(10)
                .build();

        LeadDistributionResponse response = leadDistributionService.previewDistribution(request);
        assertEquals(10, response.getTotalAssigned());
        UserDistributionSummaryDTO u1 = response.getUsers().stream().filter(u -> u.getUserId().equals(user1.getId())).findFirst().orElseThrow();
        UserDistributionSummaryDTO u2 = response.getUsers().stream().filter(u -> u.getUserId().equals(user2.getId())).findFirst().orElseThrow();
        assertEquals(5, u1.getAssignedCount());
        assertEquals(5, u2.getAssignedCount());
    }

    @Test
    void testAcceptanceCriteria6_UnequalCapacities_RespectsUserLimits() throws BadRequestException, UnauthorizedException {
        List<Lead> leads = createMockLeads(10);
        List<UUID> leadIds = leads.stream().map(Lead::getId).toList();
        List<UUID> userIds = List.of(user1.getId(), user2.getId(), user3.getId());

        when(leadRepository.findAllById(leadIds)).thenReturn(leads);
        when(userRepository.findAllById(userIds)).thenReturn(List.of(user1, user2, user3));

        // User 1: today followups = 29 -> capacity = 1
        // User 2: today followups = 0, raw = 35 -> capacity = 5
        // User 3: today followups = 0, raw = 0 -> capacity = 30
        List<Object[]> followUpRows = new ArrayList<>();
        followUpRows.add(new Object[]{user1.getId(), 29L});
        List<Object[]> rawRows = new ArrayList<>();
        rawRows.add(new Object[]{user2.getId(), 35L});

        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any())).thenReturn(followUpRows);
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId))).thenReturn(rawRows);

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .leadIds(leadIds)
                .userIds(userIds)
                .maximumNumber(10)
                .build();

        LeadDistributionResponse response = leadDistributionService.previewDistribution(request);
        assertEquals(10, response.getTotalAssigned());

        UserDistributionSummaryDTO u1 = response.getUsers().stream().filter(u -> u.getUserId().equals(user1.getId())).findFirst().orElseThrow();
        UserDistributionSummaryDTO u2 = response.getUsers().stream().filter(u -> u.getUserId().equals(user2.getId())).findFirst().orElseThrow();
        UserDistributionSummaryDTO u3 = response.getUsers().stream().filter(u -> u.getUserId().equals(user3.getId())).findFirst().orElseThrow();

        assertEquals(1, u1.getAssignedCount());
        assertEquals(5, u2.getAssignedCount());
        assertEquals(4, u3.getAssignedCount());
    }

    @Test
    void testAcceptanceCriteria7_DeletedOrInaccessibleLeadInSelection_NeverSubstitutedFromPool() throws BadRequestException, UnauthorizedException {
        Lead validLead = createMockLeads(1).get(0);
        UUID missingOrDeletedId = UUID.randomUUID();
        List<UUID> requestedIds = List.of(validLead.getId(), missingOrDeletedId);
        List<UUID> userIds = List.of(user1.getId());

        // leadRepository only returns the valid lead
        when(leadRepository.findAllById(requestedIds)).thenReturn(List.of(validLead));
        when(userRepository.findAllById(userIds)).thenReturn(List.of(user1));
        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any())).thenReturn(Collections.emptyList());
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId))).thenReturn(Collections.emptyList());

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .leadIds(requestedIds)
                .userIds(userIds)
                .build();

        LeadDistributionResponse response = leadDistributionService.previewDistribution(request);
        assertEquals(1, response.getTotalSelectedLeads());
        assertEquals(1, response.getTotalAssigned());
        verify(leadRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class));
    }

    @Test
    void testRoundRobin_FourLeadsAcrossThreeUsers_TwoOneOne() throws BadRequestException, UnauthorizedException {
        // User 1: Head, User 2: Lead, User 3: Abhishek
        User headUser = User.builder().username("head").firstName("Head").active(true).build();
        headUser.setId(UUID.randomUUID());

        User leadUser = User.builder().username("lead").firstName("Lead").active(true).build();
        leadUser.setId(UUID.randomUUID());

        User abhishekUser = User.builder().username("abhishek").firstName("Abhishek").active(true).build();
        abhishekUser.setId(UUID.randomUUID());

        List<Lead> fourLeads = createMockLeads(4);
        List<UUID> leadIds = fourLeads.stream().map(Lead::getId).toList();
        List<UUID> userIds = List.of(headUser.getId(), leadUser.getId(), abhishekUser.getId());

        when(leadRepository.findAllById(leadIds)).thenReturn(fourLeads);
        when(userRepository.findAllById(userIds)).thenReturn(List.of(headUser, leadUser, abhishekUser));
        when(leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(any(), any(), any())).thenReturn(Collections.emptyList());
        when(leadRepository.countCurrentRawLeadsGroupedByUserIds(any(), eq(rawStatusId))).thenReturn(Collections.emptyList());

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .leadIds(leadIds)
                .userIds(userIds)
                .maximumNumber(4)
                .build();

        LeadDistributionResponse response = leadDistributionService.previewDistribution(request);

        assertNotNull(response);
        assertEquals(4, response.getTotalSelectedLeads());
        assertEquals(4, response.getTotalAssigned());
        assertEquals(0, response.getTotalUnassigned());

        UserDistributionSummaryDTO headSummary = response.getUsers().stream().filter(u -> u.getUserId().equals(headUser.getId())).findFirst().orElseThrow();
        UserDistributionSummaryDTO leadSummary = response.getUsers().stream().filter(u -> u.getUserId().equals(leadUser.getId())).findFirst().orElseThrow();
        UserDistributionSummaryDTO abhishekSummary = response.getUsers().stream().filter(u -> u.getUserId().equals(abhishekUser.getId())).findFirst().orElseThrow();

        // Round 1: Head -> Lead 1, Lead -> Lead 2, Abhishek -> Lead 3
        // Round 2: Head -> Lead 4
        // Final: Head = 2, Lead = 1, Abhishek = 1
        assertEquals(2, headSummary.getAssignedCount());
        assertEquals(1, leadSummary.getAssignedCount());
        assertEquals(1, abhishekSummary.getAssignedCount());

        assertEquals(fourLeads.get(0).getId(), headSummary.getAssignedLeadIds().get(0));
        assertEquals(fourLeads.get(3).getId(), headSummary.getAssignedLeadIds().get(1));
        assertEquals(fourLeads.get(1).getId(), leadSummary.getAssignedLeadIds().get(0));
        assertEquals(fourLeads.get(2).getId(), abhishekSummary.getAssignedLeadIds().get(0));
    }
}
