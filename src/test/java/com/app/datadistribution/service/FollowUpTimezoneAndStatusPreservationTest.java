package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.config.FlexibleLocalDateTimeDeserializer;
import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.dto.lead.RescheduleFollowUpRequest;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.scheduler.FollowUpTransitionScheduler;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.LeadFollowUpServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FollowUpTimezoneAndStatusPreservationTest {

    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ILeadDataScopeService leadDataScopeService;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LeadFollowUpServiceImpl leadFollowUpService;

    @InjectMocks
    private FollowUpTransitionScheduler transitionScheduler;

    private User counselorUser;
    private Lead lead;
    private UUID leadId;
    private UUID counselorId;
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws UnauthorizedException, BadRequestException {
        counselorId = UUID.randomUUID();
        counselorUser = User.builder()
                .username("counselor1")
                .roles(Set.of(Role.builder().name(RoleType.COUNSELOR.name()).build()))
                .build();
        counselorUser.setId(counselorId);

        leadId = UUID.randomUUID();
        lead = Lead.builder()
                .leadCode("LEAD-999")
                .assignedTo(counselorUser)
                .build();
        lead.setId(leadId);

        // Security Context
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("counselor1");
        when(authentication.getPrincipal()).thenReturn("counselor1");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("counselor1")).thenReturn(Optional.of(counselorUser));
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(leadRepository.findByIdForUpdate(leadId)).thenReturn(Optional.of(lead));
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(
                UserDataScope.builder()
                        .scopeType(ScopeType.SELF)
                        .userId(counselorId)
                        .currentUser(counselorUser)
                        .build()
        );

        objectMapper = new ObjectMapper();
        JavaTimeModule module = new JavaTimeModule();
        module.addDeserializer(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
        objectMapper.registerModule(module);
    }

    @Test
    @DisplayName("1. Deserialization: Local date-time 2026-09-09T00:00 -> 2026-09-09 00:00:00")
    void testDeserializeLocalMidnight_PreservesExactDate() throws Exception {
        String json = "{\"followUpDate\":\"2026-09-09T00:00:00\",\"remarks\":\"Call lead\"}";
        LeadFollowUpRequest request = objectMapper.readValue(json, LeadFollowUpRequest.class);

        assertNotNull(request.getFollowUpDate());
        assertEquals(LocalDateTime.of(2026, 9, 9, 0, 0, 0), request.getFollowUpDate());
        assertEquals(LocalDate.of(2026, 9, 9), request.getFollowUpDate().toLocalDate());
    }

    @Test
    @DisplayName("2. Deserialization: Local date-time 2026-09-09 11:30:00 -> 2026-09-09 11:30:00")
    void testDeserializeLocalTime_PreservesExactDateTime() throws Exception {
        String json = "{\"followUpDate\":\"2026-09-09T11:30:00\",\"remarks\":\"Morning call\"}";
        LeadFollowUpRequest request = objectMapper.readValue(json, LeadFollowUpRequest.class);

        assertNotNull(request.getFollowUpDate());
        assertEquals(LocalDateTime.of(2026, 9, 9, 11, 30, 0), request.getFollowUpDate());
        assertEquals(LocalDate.of(2026, 9, 9), request.getFollowUpDate().toLocalDate());
    }

    @Test
    @DisplayName("3. Deserialization: ISO string with UTC 'Z' 2026-09-09T00:00:00.000Z -> preserves 2026-09-09 00:00:00 local datetime")
    void testDeserializeUtcZString_PreservesExactLocalDate() throws Exception {
        String json = "{\"followUpDate\":\"2026-09-09T00:00:00.000Z\",\"remarks\":\"UTC client\"}";
        LeadFollowUpRequest request = objectMapper.readValue(json, LeadFollowUpRequest.class);

        assertNotNull(request.getFollowUpDate());
        assertEquals(LocalDateTime.of(2026, 9, 9, 0, 0, 0), request.getFollowUpDate());
        assertEquals(LocalDate.of(2026, 9, 9), request.getFollowUpDate().toLocalDate());
    }

    @Test
    @DisplayName("4. Deserialization: Date-only 2026-09-09 -> 2026-09-09 00:00:00")
    void testDeserializeDateOnly_ParsesAtStartOfDay() throws Exception {
        String json = "{\"followUpDate\":\"2026-09-09\",\"remarks\":\"Date only format\"}";
        LeadFollowUpRequest request = objectMapper.readValue(json, LeadFollowUpRequest.class);

        assertNotNull(request.getFollowUpDate());
        assertEquals(LocalDateTime.of(2026, 9, 9, 0, 0, 0), request.getFollowUpDate());
        assertEquals(LocalDate.of(2026, 9, 9), request.getFollowUpDate().toLocalDate());
    }

    @Test
    @DisplayName("5. Status Logic: Scheduled for today -> PENDING")
    void testCreateFollowUpToday_AssignsPendingStatus() throws Exception {
        LocalDate today = LocalDate.now(IST_ZONE);
        LocalDateTime todayFollowUp = today.atStartOfDay();

        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .followUpDate(todayFollowUp)
                .remarks("Discussion today")
                .build();

        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(leadId)).thenReturn(false);
        when(leadFollowUpRepository.save(any(LeadFollowUp.class))).thenAnswer(invocation -> {
            LeadFollowUp f = invocation.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });
        when(leadMapper.toDto(any(LeadFollowUp.class))).thenAnswer(invocation -> {
            LeadFollowUp f = invocation.getArgument(0);
            return LeadFollowUpResponse.builder()
                    .id(f.getId())
                    .followUpDate(f.getFollowUpDate())
                    .status(f.getStatus())
                    .completed(f.isCompleted())
                    .remarks(f.getRemarks())
                    .build();
        });

        LeadFollowUpResponse response = leadFollowUpService.createFollowUp(leadId, request);

        assertNotNull(response);
        assertEquals(FollowUpStatus.PENDING, response.getStatus());
        assertEquals(today, response.getFollowUpDate().toLocalDate());
    }

    @Test
    @DisplayName("6. Status Logic: Scheduled for future -> UPCOMING")
    void testCreateFollowUpFuture_AssignsUpcomingStatus() throws Exception {
        LocalDate futureDate = LocalDate.now(IST_ZONE).plusDays(2);
        LocalDateTime futureFollowUp = futureDate.atTime(11, 30);

        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .followUpDate(futureFollowUp)
                .remarks("Discussion future")
                .build();

        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(leadId)).thenReturn(false);
        when(leadFollowUpRepository.save(any(LeadFollowUp.class))).thenAnswer(invocation -> {
            LeadFollowUp f = invocation.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });
        when(leadMapper.toDto(any(LeadFollowUp.class))).thenAnswer(invocation -> {
            LeadFollowUp f = invocation.getArgument(0);
            return LeadFollowUpResponse.builder()
                    .id(f.getId())
                    .followUpDate(f.getFollowUpDate())
                    .status(f.getStatus())
                    .completed(f.isCompleted())
                    .remarks(f.getRemarks())
                    .build();
        });

        LeadFollowUpResponse response = leadFollowUpService.createFollowUp(leadId, request);

        assertNotNull(response);
        assertEquals(FollowUpStatus.UPCOMING, response.getStatus());
        assertEquals(futureDate, response.getFollowUpDate().toLocalDate());
    }

    @Test
    @DisplayName("7. Status Logic: Scheduled for past -> Throws BadRequestException")
    void testCreateFollowUpPast_ThrowsBadRequestException() {
        LocalDate pastDate = LocalDate.now(IST_ZONE).minusDays(1);
        LocalDateTime pastFollowUp = pastDate.atStartOfDay();

        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .followUpDate(pastFollowUp)
                .remarks("Past follow up")
                .build();

        when(leadFollowUpRepository.existsActiveFollowUpByLeadId(leadId)).thenReturn(false);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                leadFollowUpService.createFollowUp(leadId, request));

        assertTrue(ex.getMessage().contains("cannot be in the past"));
    }

    @Test
    @DisplayName("8. Transition Scheduler: UPCOMING -> PENDING for today; PENDING -> MISSED for past date")
    void testTransitionScheduler_TransitionsStatusesCorrectly() {
        LocalDate today = LocalDate.now(IST_ZONE);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        when(leadFollowUpRepository.transitionUpcomingToPendingForDate(endOfDay)).thenReturn(5);
        when(leadFollowUpRepository.transitionPendingToMissedForDate(startOfDay)).thenReturn(3);

        transitionScheduler.transitionFollowUpStatuses();

        verify(leadFollowUpRepository).transitionUpcomingToPendingForDate(eq(endOfDay));
        verify(leadFollowUpRepository).transitionPendingToMissedForDate(eq(startOfDay));
    }

    @Test
    @DisplayName("9. Status Logic: COMPLETED, CANCELLED, NOT_CONNECTED followups are preserved")
    void testTerminalStatuses_CannotBeRescheduledOrOverwritten() {
        UUID followUpId = UUID.randomUUID();
        LeadFollowUp completedFollowUp = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(LocalDate.now(IST_ZONE).minusDays(2).atStartOfDay())
                .status(FollowUpStatus.COMPLETED)
                .completed(true)
                .assignedTo(counselorUser)
                .build();
        completedFollowUp.setId(followUpId);

        when(leadFollowUpRepository.findById(followUpId)).thenReturn(Optional.of(completedFollowUp));

        RescheduleFollowUpRequest req = RescheduleFollowUpRequest.builder()
                .newFollowUpDate(LocalDate.now(IST_ZONE).plusDays(1).atStartOfDay())
                .remarks("Trying to reschedule completed")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                leadFollowUpService.rescheduleFollowUp(followUpId, req));

        assertTrue(ex.getMessage().contains("Cannot reschedule a completed or cancelled follow-up"));
    }
}
