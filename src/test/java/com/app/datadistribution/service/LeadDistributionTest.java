package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.app.datadistribution.dto.lead.*;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAssignmentHistory;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.LeadAssignmentHistoryRepository;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.impl.LeadDistributionServiceImpl;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

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

    @InjectMocks
    private LeadDistributionServiceImpl leadDistributionService;

    private User adminUser;
    private User userA;
    private User userB;
    private User userC;
    private Lead lead1;
    private Lead lead2;
    private Lead lead3;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(leadDistributionService, "maxDailyFollowups", 30);

        adminUser = User.builder().username("admin").active(true).build();
        adminUser.setId(UUID.randomUUID());

        userA = User.builder().username("userA").firstName("User").lastName("A").email("userA@test.com").active(true).build();
        userA.setId(UUID.randomUUID());

        userB = User.builder().username("userB").firstName("User").lastName("B").email("userB@test.com").active(true).build();
        userB.setId(UUID.randomUUID());

        userC = User.builder().username("userC").firstName("User").lastName("C").email("userC@test.com").active(true).build();
        userC.setId(UUID.randomUUID());

        lead1 = Lead.builder().leadCode("LEAD-001").fullName("Lead One").build();
        lead1.setId(UUID.randomUUID());

        lead2 = Lead.builder().leadCode("LEAD-002").fullName("Lead Two").build();
        lead2.setId(UUID.randomUUID());

        lead3 = Lead.builder().leadCode("LEAD-003").fullName("Lead Three").build();
        lead3.setId(UUID.randomUUID());

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("admin");
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
    }

    @Test
    void testPreviewDistribution_CalculatesCapacityAndSkippingCorrectly() throws BadRequestException, UnauthorizedException {
        when(leadRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(lead1, lead2, lead3));
        when(userRepository.findById(userA.getId())).thenReturn(Optional.of(userA));
        when(userRepository.findById(userB.getId())).thenReturn(Optional.of(userB));

        // User A: 32 follow-ups today (Limit reached)
        lenient().when(leadFollowUpRepository.countScheduledFollowUpsForUserBetween(eq(userA.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(32L);
        lenient().when(leadRepository.countUnavailedLeadsByUserId(userA.getId())).thenReturn(5L);

        // User B: 12 follow-ups today, 22 unavailed leads, max 40 => capacity 18
        lenient().when(leadFollowUpRepository.countScheduledFollowUpsForUserBetween(eq(userB.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(12L);
        lenient().when(leadRepository.countUnavailedLeadsByUserId(userB.getId())).thenReturn(22L);

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .userIds(List.of(userA.getId(), userB.getId()))
                .maximumDataPerUser(40)
                .filters(LeadDistributionFilterRequest.builder().build())
                .build();

        LeadDistributionResponse response = leadDistributionService.previewDistribution(request);

        assertNotNull(response);
        assertTrue(response.isPreviewOnly());
        assertEquals(3, response.getTotalMatchingLeads());
        assertEquals(3, response.getTotalAvailableLeads());
        assertEquals(3, response.getTotalAssigned());

        UserDistributionSummaryDTO summaryA = response.getUsers().stream().filter(u -> u.getUserId().equals(userA.getId())).findFirst().orElseThrow();
        assertEquals("SKIPPED", summaryA.getStatus());
        assertEquals("DAILY_FOLLOWUP_LIMIT_REACHED", summaryA.getReason());
        assertEquals(0, summaryA.getAssignedCount());

        UserDistributionSummaryDTO summaryB = response.getUsers().stream().filter(u -> u.getUserId().equals(userB.getId())).findFirst().orElseThrow();
        assertEquals("SUCCESS", summaryB.getStatus());
        assertEquals(18, summaryB.getRemainingCapacity());
        assertEquals(3, summaryB.getAssignedCount());

        // Verify preview did NOT save assignments
        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    void testDistributeLeads_ExecutesAssignmentAndLogsAudit() throws BadRequestException, UnauthorizedException {
        lenient().when(leadRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(lead1, lead2));
        lenient().when(userRepository.findById(userB.getId())).thenReturn(Optional.of(userB));
        lenient().when(leadFollowUpRepository.countScheduledFollowUpsForUserBetween(eq(userB.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(10L);
        lenient().when(leadRepository.countUnavailedLeadsByUserId(userB.getId())).thenReturn(20L);
        lenient().when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .userIds(List.of(userB.getId()))
                .maximumDataPerUser(40)
                .filters(LeadDistributionFilterRequest.builder().build())
                .build();

        LeadDistributionResponse response = leadDistributionService.distributeLeads(request);

        assertNotNull(response);
        assertFalse(response.isPreviewOnly());
        assertEquals(2, response.getTotalAssigned());

        verify(leadRepository, times(2)).save(any(Lead.class));
        verify(leadAssignmentHistoryRepository, times(2)).save(any(LeadAssignmentHistory.class));
        assertEquals(userB, lead1.getAssignedTo());
        assertEquals(userB, lead2.getAssignedTo());
    }

    @Test
    void testDistributeLeads_UserWithMaxUnavailedLeads_Skipped() throws BadRequestException, UnauthorizedException {
        lenient().when(leadRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(lead1));
        lenient().when(userRepository.findById(userC.getId())).thenReturn(Optional.of(userC));
        lenient().when(leadFollowUpRepository.countScheduledFollowUpsForUserBetween(eq(userC.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(5L);
        lenient().when(leadRepository.countUnavailedLeadsByUserId(userC.getId())).thenReturn(40L);

        LeadDistributionRequest request = LeadDistributionRequest.builder()
                .userIds(List.of(userC.getId()))
                .maximumDataPerUser(40)
                .filters(LeadDistributionFilterRequest.builder().build())
                .build();

        LeadDistributionResponse response = leadDistributionService.distributeLeads(request);

        assertNotNull(response);
        assertEquals(0, response.getTotalAssigned());

        UserDistributionSummaryDTO summaryC = response.getUsers().get(0);
        assertEquals("SKIPPED", summaryC.getStatus());
        assertEquals("MAX_CAPACITY_REACHED", summaryC.getReason());

        verify(leadRepository, never()).save(any(Lead.class));
    }
}
