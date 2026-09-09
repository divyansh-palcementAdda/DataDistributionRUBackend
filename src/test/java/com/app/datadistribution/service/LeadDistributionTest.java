package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.app.datadistribution.dto.lead.*;
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
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

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
}
