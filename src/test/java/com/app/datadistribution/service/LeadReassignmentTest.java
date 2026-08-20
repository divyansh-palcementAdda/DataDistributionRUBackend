package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.app.datadistribution.dto.reassign.*;
import com.app.datadistribution.entity.*;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.*;
import com.app.datadistribution.service.impl.LeadReassignmentServiceImpl;
import com.app.datadistribution.service.interfaces.IActivityLogService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LeadReassignmentTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;
    @Mock
    private LeadAssignmentHistoryRepository leadAssignmentHistoryRepository;
    @Mock
    private FollowUpAssignmentHistoryRepository followUpAssignmentHistoryRepository;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private IActivityLogService activityLogService;
    @Mock
    private IUserDataScopeService dataScopeService;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private LeadReassignmentServiceImpl leadReassignmentService;

    private User adminUser;
    private User sourceUser;
    private User targetUser1;
    private User targetUser2;
    private Lead testLead;
    private LeadFollowUp testFollowUp1;
    private LeadFollowUp testFollowUp2;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(leadReassignmentService, "maxDailyFollowUps", 30);

        com.app.datadistribution.service.dto.UserDataScope systemScope = com.app.datadistribution.service.dto.UserDataScope.builder()
                .scopeType(com.app.datadistribution.service.dto.UserDataScope.ScopeType.SYSTEM)
                .build();
        lenient().when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);

        Role adminRole = Role.builder().name(RoleType.SUPER_ADMIN.name()).build();
        adminUser = User.builder().username("admin").active(true).roles(Set.of(adminRole)).build();
        adminUser.setId(UUID.randomUUID());

        sourceUser = User.builder().username("source").firstName("Source").lastName("User").active(true).roles(Set.of()).build();
        sourceUser.setId(UUID.randomUUID());

        targetUser1 = User.builder().username("target1").firstName("Target").lastName("One").active(true).roles(Set.of()).build();
        targetUser1.setId(UUID.randomUUID());

        targetUser2 = User.builder().username("target2").firstName("Target").lastName("Two").active(true).roles(Set.of()).build();
        targetUser2.setId(UUID.randomUUID());

        testLead = Lead.builder().fullName("Student Test").phoneNumber("9999999999").email("student@test.com").assignedTo(sourceUser).build();
        testLead.setId(UUID.randomUUID());

        testFollowUp1 = LeadFollowUp.builder().lead(testLead).followUpDate(LocalDateTime.now()).status(FollowUpStatus.PENDING).completed(false).assignedTo(sourceUser).build();
        testFollowUp1.setId(UUID.randomUUID());

        testFollowUp2 = LeadFollowUp.builder().lead(testLead).followUpDate(LocalDateTime.now().plusHours(1)).status(FollowUpStatus.PENDING).completed(false).assignedTo(sourceUser).build();
        testFollowUp2.setId(UUID.randomUUID());

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("admin");
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
    }

    @Test
    void testReassignFollowUps_SingleTargetUser_Success() throws Exception {
        lenient().when(userRepository.findById(sourceUser.getId())).thenReturn(Optional.of(sourceUser));
        lenient().when(userRepository.findById(targetUser1.getId())).thenReturn(Optional.of(targetUser1));
        lenient().when(leadFollowUpRepository.findById(testFollowUp1.getId())).thenReturn(Optional.of(testFollowUp1));
        lenient().when(leadFollowUpRepository.countScheduledFollowUpsForUserBetween(any(), any(), any())).thenReturn(5L);

        FollowUpReassignRequest request = FollowUpReassignRequest.builder()
                .sourceUserId(sourceUser.getId())
                .reason("User absent")
                .assignments(List.of(
                        FollowUpReassignItemDTO.builder()
                                .targetUserId(targetUser1.getId())
                                .followUpIds(List.of(testFollowUp1.getId()))
                                .build()
                ))
                .build();

        FollowUpReassignResponse response = leadReassignmentService.reassignFollowUps(request);

        assertNotNull(response);
        assertEquals(1, response.getTotalReassigned());
        assertEquals(targetUser1.getId(), testFollowUp1.getAssignedTo().getId());
        verify(followUpAssignmentHistoryRepository, times(1)).save(any(FollowUpAssignmentHistory.class));
    }

    @Test
    void testReassignFollowUps_MultiTargetDistribution_Success() throws Exception {
        lenient().when(userRepository.findById(sourceUser.getId())).thenReturn(Optional.of(sourceUser));
        lenient().when(userRepository.findById(targetUser1.getId())).thenReturn(Optional.of(targetUser1));
        lenient().when(userRepository.findById(targetUser2.getId())).thenReturn(Optional.of(targetUser2));

        lenient().when(leadFollowUpRepository.findById(testFollowUp1.getId())).thenReturn(Optional.of(testFollowUp1));
        lenient().when(leadFollowUpRepository.findById(testFollowUp2.getId())).thenReturn(Optional.of(testFollowUp2));
        lenient().when(leadFollowUpRepository.countScheduledFollowUpsForUserBetween(any(), any(), any())).thenReturn(2L);

        FollowUpReassignRequest request = FollowUpReassignRequest.builder()
                .sourceUserId(sourceUser.getId())
                .reason("Distribute absent user follow-ups")
                .assignments(List.of(
                        FollowUpReassignItemDTO.builder().targetUserId(targetUser1.getId()).followUpIds(List.of(testFollowUp1.getId())).build(),
                        FollowUpReassignItemDTO.builder().targetUserId(targetUser2.getId()).followUpIds(List.of(testFollowUp2.getId())).build()
                ))
                .build();

        FollowUpReassignResponse response = leadReassignmentService.reassignFollowUps(request);

        assertNotNull(response);
        assertEquals(2, response.getTotalReassigned());
        assertEquals(2, response.getAssignments().size());
        assertEquals(targetUser1.getId(), testFollowUp1.getAssignedTo().getId());
        assertEquals(targetUser2.getId(), testFollowUp2.getAssignedTo().getId());
        verify(followUpAssignmentHistoryRepository, times(2)).save(any(FollowUpAssignmentHistory.class));
    }

    @Test
    void testReassignLeads_WithPendingFollowUpsCascade_Success() throws Exception {
        lenient().when(userRepository.findById(sourceUser.getId())).thenReturn(Optional.of(sourceUser));
        lenient().when(userRepository.findById(targetUser1.getId())).thenReturn(Optional.of(targetUser1));
        lenient().when(leadRepository.findById(testLead.getId())).thenReturn(Optional.of(testLead));
        lenient().when(leadFollowUpRepository.findPendingUncompletedFollowUpsByLeadIds(any(), any())).thenReturn(List.of(testFollowUp1, testFollowUp2));

        LeadReassignRequest request = LeadReassignRequest.builder()
                .sourceUserId(sourceUser.getId())
                .reason("Reassign client leads")
                .reassignRelatedPendingFollowUps(true)
                .assignments(List.of(
                        LeadReassignItemDTO.builder().targetUserId(targetUser1.getId()).leadIds(List.of(testLead.getId())).build()
                ))
                .build();

        LeadReassignResponse response = leadReassignmentService.reassignLeads(request);

        assertNotNull(response);
        assertEquals(1, response.getTotalReassigned());
        assertEquals(2, response.getTotalPendingFollowUpsReassigned());
        assertEquals(targetUser1.getId(), testLead.getAssignedTo().getId());
        assertEquals(targetUser1.getId(), testFollowUp1.getAssignedTo().getId());
        assertEquals(targetUser1.getId(), testFollowUp2.getAssignedTo().getId());

        verify(leadAssignmentHistoryRepository, times(1)).save(any(LeadAssignmentHistory.class));
        verify(followUpAssignmentHistoryRepository, times(2)).save(any(FollowUpAssignmentHistory.class));
    }

    @Test
    void testReassignFollowUps_WorkloadExceededWithoutOverride_ThrowsException() {
        lenient().when(userRepository.findById(sourceUser.getId())).thenReturn(Optional.of(sourceUser));
        lenient().when(userRepository.findById(targetUser1.getId())).thenReturn(Optional.of(targetUser1));
        lenient().when(leadFollowUpRepository.findById(testFollowUp1.getId())).thenReturn(Optional.of(testFollowUp1));

        // Target user already has 30 follow-ups today (max limit = 30)
        lenient().when(leadFollowUpRepository.countScheduledFollowUpsForUserBetween(any(), any(), any())).thenReturn(30L);

        FollowUpReassignRequest request = FollowUpReassignRequest.builder()
                .sourceUserId(sourceUser.getId())
                .allowWorkloadOverride(false)
                .assignments(List.of(
                        FollowUpReassignItemDTO.builder().targetUserId(targetUser1.getId()).followUpIds(List.of(testFollowUp1.getId())).build()
                ))
                .build();

        assertThrows(BadRequestException.class, () -> leadReassignmentService.reassignFollowUps(request));
    }
}
