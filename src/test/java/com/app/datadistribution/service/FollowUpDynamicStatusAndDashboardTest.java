package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest;
import com.app.datadistribution.dto.dashboard.DashboardFollowUpCountResponseDTO;
import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.dto.lead.RescheduleFollowUpRequest;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.DashboardAnalyticsRepository;
import com.app.datadistribution.repository.DashboardCardRepository;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserDashboardCardPreferenceRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.DashboardServiceImpl;
import com.app.datadistribution.service.impl.LeadFollowUpServiceImpl;
import com.app.datadistribution.service.interfaces.IDashboardCardPermissionService;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FollowUpDynamicStatusAndDashboardTest {

        @Mock
        private LeadFollowUpRepository leadFollowUpRepository;
        @Mock
        private LeadRepository leadRepository;
        @Mock
        private LeadStatusRepository leadStatusRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private IUserDataScopeService userDataScopeService;
        @Mock
        private ILeadDataScopeService leadDataScopeService;
        @Mock
        private LeadMapper leadMapper;
        @Mock
        private EntityManager entityManager;
        @Mock
        private ApplicationEventPublisher eventPublisher;
        @Mock
        private DashboardCardRepository dashboardCardRepository;
        @Mock
        private UserDashboardCardPreferenceRepository userPreferenceRepository;
        @Mock
        private DashboardAnalyticsRepository dashboardAnalyticsRepository;
        @Mock
        private IDashboardCardPermissionService dashboardCardPermissionService;

        @InjectMocks
        private LeadFollowUpServiceImpl leadFollowUpService;

        @InjectMocks
        private DashboardServiceImpl dashboardService;

        private User counselorUser;
        private User hodUser;
        private User adminUser;
        private Lead lead;
        private UUID leadId;
        private UUID counselorId;
        private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

        @BeforeEach
        void setUp() {
                counselorId = UUID.randomUUID();
                counselorUser = User.builder()
                                .username("counselor1")
                                .firstName("Rohan")
                                .lastName("Verma")
                                .email("rohan@example.com")
                                .roles(Set.of(Role.builder().name(RoleType.COUNSELOR.name()).build()))
                                .active(true)
                                .build();
                counselorUser.setId(counselorId);

                hodUser = User.builder()
                                .username("hod1")
                                .firstName("Anil")
                                .lastName("Kapoor")
                                .roles(Set.of(Role.builder().name(RoleType.HOD.name()).build()))
                                .active(true)
                                .build();
                hodUser.setId(UUID.randomUUID());

                adminUser = User.builder()
                                .username("admin1")
                                .firstName("Super")
                                .lastName("Admin")
                                .roles(Set.of(Role.builder().name(RoleType.SUPER_ADMIN.name()).build()))
                                .active(true)
                                .build();
                adminUser.setId(UUID.randomUUID());

                leadId = UUID.randomUUID();
                lead = Lead.builder()
                                .leadCode("LEAD-999")
                                .fullName("Pooja Sharma")
                                .assignedTo(counselorUser)
                                .build();
                lead.setId(leadId);

                Authentication auth = mock(Authentication.class);
                when(auth.isAuthenticated()).thenReturn(true);
                when(auth.getName()).thenReturn("counselor1");
                when(auth.getPrincipal()).thenReturn("counselor1");
                SecurityContext securityContext = mock(SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(auth);
                SecurityContextHolder.setContext(securityContext);

                when(userRepository.findByUsername("counselor1")).thenReturn(Optional.of(counselorUser));
                when(userRepository.findByUsername("hod1")).thenReturn(Optional.of(hodUser));
                when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(adminUser));
        }

        @Test
        @DisplayName("Create Follow-up for future date -> automatically assigned UPCOMING status")
        void testCreateFollowUp_FutureDate_AssignedUpcoming() throws Exception {
                LocalDateTime futureDate = LocalDate.now(IST_ZONE).plusDays(3).atTime(14, 30);
                LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                                .leadId(leadId)
                                .followUpDate(futureDate)
                                .remarks("Discussion scheduled for next week")
                                .build();

                when(leadRepository.findByIdForUpdate(leadId)).thenReturn(Optional.of(lead));
                when(leadFollowUpRepository.existsActiveFollowUpByLeadId(leadId)).thenReturn(false);
                when(leadFollowUpRepository.save(any(LeadFollowUp.class))).thenAnswer(inv -> {
                        LeadFollowUp f = inv.getArgument(0);
                        f.setId(UUID.randomUUID());
                        return f;
                });
                when(leadMapper.toDto(any(LeadFollowUp.class))).thenAnswer(inv -> {
                        LeadFollowUp f = inv.getArgument(0);
                        return LeadFollowUpResponse.builder()
                                        .id(f.getId())
                                        .followUpDate(f.getFollowUpDate())
                                        .remarks(f.getRemarks())
                                        .status(f.getStatus())
                                        .build();
                });

                LeadFollowUpResponse response = leadFollowUpService.createFollowUp(leadId, request);

                assertNotNull(response);
                assertEquals(FollowUpStatus.UPCOMING, response.getStatus());
                verify(leadFollowUpRepository, times(1)).save(argThat(f -> f.getStatus() == FollowUpStatus.UPCOMING));
        }

        @Test
        @DisplayName("Create Follow-up for today's date -> automatically assigned PENDING status")
        void testCreateFollowUp_TodayDate_AssignedPending() throws Exception {
                LocalDateTime todayDate = LocalDate.now(IST_ZONE).atTime(16, 0);
                LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                                .leadId(leadId)
                                .followUpDate(todayDate)
                                .remarks("Call today evening")
                                .build();

                when(leadRepository.findByIdForUpdate(leadId)).thenReturn(Optional.of(lead));
                when(leadFollowUpRepository.existsActiveFollowUpByLeadId(leadId)).thenReturn(false);
                when(leadFollowUpRepository.save(any(LeadFollowUp.class))).thenAnswer(inv -> {
                        LeadFollowUp f = inv.getArgument(0);
                        f.setId(UUID.randomUUID());
                        return f;
                });
                when(leadMapper.toDto(any(LeadFollowUp.class))).thenAnswer(inv -> {
                        LeadFollowUp f = inv.getArgument(0);
                        return LeadFollowUpResponse.builder()
                                        .id(f.getId())
                                        .followUpDate(f.getFollowUpDate())
                                        .remarks(f.getRemarks())
                                        .status(f.getStatus())
                                        .build();
                });

                LeadFollowUpResponse response = leadFollowUpService.createFollowUp(leadId, request);

                assertNotNull(response);
                assertEquals(FollowUpStatus.PENDING, response.getStatus());
                verify(leadFollowUpRepository, times(1)).save(argThat(f -> f.getStatus() == FollowUpStatus.PENDING));
        }

        @Test
        @DisplayName("Create Follow-up for past date -> Throws BadRequestException")
        void testCreateFollowUp_PastDate_ThrowsException() {
                LocalDateTime pastDate = LocalDate.now(IST_ZONE).minusDays(1).atTime(10, 0);
                LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                                .leadId(leadId)
                                .followUpDate(pastDate)
                                .remarks("Attempted past date")
                                .build();

                when(leadRepository.findByIdForUpdate(leadId)).thenReturn(Optional.of(lead));
                when(leadFollowUpRepository.existsActiveFollowUpByLeadId(leadId)).thenReturn(false);

                BadRequestException ex = assertThrows(BadRequestException.class,
                                () -> leadFollowUpService.createFollowUp(leadId, request));
                assertTrue(ex.getMessage().contains("past"));
        }

        @Test
        @DisplayName("Client attempts status tampering (sends COMPLETED in request) -> backend ignores it and sets correct initial status")
        void testCreateFollowUp_ClientStatusTampering_BackendEnforcesCorrectStatus() throws Exception {
                LocalDateTime futureDate = LocalDate.now(IST_ZONE).plusDays(2).atTime(11, 0);
                LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                                .leadId(leadId)
                                .followUpDate(futureDate)
                                .remarks("Student requested callback")
                                .status(FollowUpStatus.COMPLETED) // Attempted client tampering
                                .build();

                when(leadRepository.findByIdForUpdate(leadId)).thenReturn(Optional.of(lead));
                when(leadFollowUpRepository.existsActiveFollowUpByLeadId(leadId)).thenReturn(false);
                when(leadFollowUpRepository.save(any(LeadFollowUp.class))).thenAnswer(inv -> {
                        LeadFollowUp f = inv.getArgument(0);
                        f.setId(UUID.randomUUID());
                        return f;
                });
                when(leadMapper.toDto(any(LeadFollowUp.class))).thenAnswer(inv -> {
                        LeadFollowUp f = inv.getArgument(0);
                        return LeadFollowUpResponse.builder()
                                        .id(f.getId())
                                        .status(f.getStatus())
                                        .build();
                });

                LeadFollowUpResponse response = leadFollowUpService.createFollowUp(leadId, request);

                assertEquals(FollowUpStatus.UPCOMING, response.getStatus());
                verify(leadFollowUpRepository, times(1)).save(argThat(f -> f.getStatus() == FollowUpStatus.UPCOMING));
        }

        @Test
        @DisplayName("Reschedule Follow-up from Today to Future -> transitions PENDING to UPCOMING")
        void testRescheduleFollowUp_ToFutureDate_TransitionsToUpcoming() throws Exception {
                UUID followUpId = UUID.randomUUID();
                LeadFollowUp followUp = LeadFollowUp.builder()
                                .lead(lead)
                                .followUpDate(LocalDate.now(IST_ZONE).atTime(10, 0))
                                .status(FollowUpStatus.PENDING)
                                .completed(false)
                                .assignedTo(counselorUser)
                                .build();
                followUp.setId(followUpId);

                LocalDateTime newFutureDate = LocalDate.now(IST_ZONE).plusDays(5).atTime(15, 0);
                RescheduleFollowUpRequest req = RescheduleFollowUpRequest.builder()
                                .newFollowUpDate(newFutureDate)
                                .remarks("Rescheduled as student is travelling")
                                .build();

                when(leadFollowUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));
                when(leadFollowUpRepository.save(any(LeadFollowUp.class))).thenAnswer(inv -> inv.getArgument(0));
                when(leadMapper.toDto(any(LeadFollowUp.class))).thenAnswer(inv -> {
                        LeadFollowUp f = inv.getArgument(0);
                        return LeadFollowUpResponse.builder()
                                        .id(f.getId())
                                        .followUpDate(f.getFollowUpDate())
                                        .status(f.getStatus())
                                        .build();
                });

                LeadFollowUpResponse response = leadFollowUpService.rescheduleFollowUp(followUpId, req);

                assertEquals(FollowUpStatus.UPCOMING, response.getStatus());
        }

        @Test
        @DisplayName("Reschedule Follow-up to Past Date -> throws BadRequestException")
        void testRescheduleFollowUp_ToPastDate_ThrowsException() {
                UUID followUpId = UUID.randomUUID();
                LeadFollowUp followUp = LeadFollowUp.builder()
                                .lead(lead)
                                .followUpDate(LocalDate.now(IST_ZONE).plusDays(2).atTime(10, 0))
                                .status(FollowUpStatus.UPCOMING)
                                .completed(false)
                                .assignedTo(counselorUser)
                                .build();
                followUp.setId(followUpId);

                LocalDateTime pastDate = LocalDate.now(IST_ZONE).minusDays(1).atTime(10, 0);
                RescheduleFollowUpRequest req = RescheduleFollowUpRequest.builder()
                                .newFollowUpDate(pastDate)
                                .remarks("Past date reschedule")
                                .build();

                when(leadFollowUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));

                BadRequestException ex = assertThrows(BadRequestException.class,
                                () -> leadFollowUpService.rescheduleFollowUp(followUpId, req));
                assertTrue(ex.getMessage().contains("past"));
        }

        @Test
        @DisplayName("Dashboard Today's Follow-ups Count Endpoint returns correct scoped count")
        void testGetTodayFollowUpsCount_ReturnsCorrectScopedCount() throws Exception {
                UserDataScope scope = UserDataScope.builder()
                                .scopeType(ScopeType.SELF)
                                .userId(counselorId)
                                .build();

                when(userDataScopeService.getScopeForCurrentUser(any(DashboardAnalyticsFilterRequest.class)))
                                .thenReturn(scope);

                CriteriaBuilder cb = mock(CriteriaBuilder.class);
                CriteriaQuery<Long> cq = mock(CriteriaQuery.class);
                Root<LeadFollowUp> root = mock(Root.class);
                Path path = mock(Path.class);
                TypedQuery<Long> typedQuery = mock(TypedQuery.class);
                Predicate pred = mock(Predicate.class);

                when(entityManager.getCriteriaBuilder()).thenReturn(cb);
                when(cb.createQuery(Long.class)).thenReturn(cq);
                when(cq.from(LeadFollowUp.class)).thenReturn(root);
                when(root.get(anyString())).thenReturn(path);
                when(path.get(anyString())).thenReturn(path);
                when(cb.equal(any(), any())).thenReturn(pred);
                when(cb.between(any(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(pred);
                when(cb.greaterThanOrEqualTo(any(), any(LocalDateTime.class))).thenReturn(pred);
                when(cb.lessThan(any(), any(LocalDateTime.class))).thenReturn(pred);
                when(cb.or(any(Predicate[].class))).thenReturn(pred);
                when(cb.and(any(Predicate[].class))).thenReturn(pred);
                when(cb.countDistinct(any())).thenReturn(mock(jakarta.persistence.criteria.Expression.class));
                when(cq.select(any())).thenReturn(cq);
                when(cq.where(any(Predicate[].class))).thenReturn(cq);
                when(entityManager.createQuery(cq)).thenReturn(typedQuery);
                when(typedQuery.getSingleResult()).thenReturn(14L);

                DashboardFollowUpCountResponseDTO result = dashboardService
                                .getTodayFollowUpsCount(new DashboardAnalyticsFilterRequest());

                assertNotNull(result);
                assertEquals(14L, result.getCount());
                assertEquals("TodayFollowUps", result.getType());
                assertEquals(LocalDate.now(IST_ZONE), result.getDate());
        }
}
