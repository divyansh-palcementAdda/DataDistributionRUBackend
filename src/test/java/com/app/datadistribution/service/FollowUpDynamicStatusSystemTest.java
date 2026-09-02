package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.dropdown.FollowUpStatusDropdownResponse;
import com.app.datadistribution.dto.dropdown.LeadStatusDropdownResponse;
import com.app.datadistribution.dto.followup.FollowUpStatusCountDTO;
import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.dto.lead.LeadStatusRequest;
import com.app.datadistribution.dto.lead.LeadStatusResponse;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.enums.SentimentCategory;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.DropdownServiceImpl;
import com.app.datadistribution.service.impl.FollowUpServiceImpl;
import com.app.datadistribution.service.impl.LeadFollowUpServiceImpl;
import com.app.datadistribution.service.impl.LeadStatusServiceImpl;
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
class FollowUpDynamicStatusSystemTest {

    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;
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
    private IDashboardCardPermissionService dashboardCardPermissionService;
    @Mock
    private com.app.datadistribution.service.interfaces.ILeadStatusTransitionService leadStatusTransitionService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DropdownServiceImpl dropdownService;

    @InjectMocks
    private FollowUpServiceImpl followUpService;

    @InjectMocks
    private LeadFollowUpServiceImpl leadFollowUpService;

    @InjectMocks
    private LeadStatusServiceImpl leadStatusService;

    private User counselor;
    private Department dept;
    private Lead lead;
    private LeadStatus rawStatus;
    private LeadStatus formFollowUpStatus;
    private LeadStatus counselingFollowUpStatus;
    private LeadStatus registeredStatus;

    @BeforeEach
    void setUp() throws Exception {
        dept = Department.builder().name("Admissions").code("ADM").active(true).build();
        dept.setId(UUID.randomUUID());

        counselor = User.builder().username("counselor1").firstName("Ravi").lastName("Sharma").active(true).build();
        counselor.setId(UUID.randomUUID());

        rawStatus = LeadStatus.builder()
                .name("Raw")
                .code("RAW")
                .displayOrder(1)
                .isFollowUpStatus(false)
                .active(true)
                .build();
        rawStatus.setId(UUID.randomUUID());

        formFollowUpStatus = LeadStatus.builder()
                .name("Form Follow-Up")
                .code("FORM_FOLLOW_UP")
                .displayOrder(7)
                .isFollowUpStatus(true)
                .active(true)
                .build();
        formFollowUpStatus.setId(UUID.randomUUID());

        counselingFollowUpStatus = LeadStatus.builder()
                .name("Counseling Follow-Up")
                .code("COUNSELING_FOLLOW_UP")
                .displayOrder(8)
                .isFollowUpStatus(true)
                .active(true)
                .build();
        counselingFollowUpStatus.setId(UUID.randomUUID());

        registeredStatus = LeadStatus.builder()
                .name("Registered")
                .code("REGISTERED")
                .displayOrder(9)
                .isFollowUpStatus(false)
                .active(true)
                .build();
        registeredStatus.setId(UUID.randomUUID());

        lead = Lead.builder()
                .leadCode("LEAD-7001")
                .fullName("Amit Verma")
                .phoneNumber("9876543210")
                .assignedTo(counselor)
                .department(dept)
                .currentStatus(rawStatus)
                .build();
        lead.setId(UUID.randomUUID());

        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        when(leadRepository.findByIdForUpdate(lead.getId())).thenReturn(Optional.of(lead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        when(leadStatusRepository.findById(rawStatus.getId())).thenReturn(Optional.of(rawStatus));
        when(leadStatusRepository.findById(formFollowUpStatus.getId())).thenReturn(Optional.of(formFollowUpStatus));
        when(leadStatusRepository.findById(counselingFollowUpStatus.getId())).thenReturn(Optional.of(counselingFollowUpStatus));
        when(leadStatusRepository.findById(registeredStatus.getId())).thenReturn(Optional.of(registeredStatus));

        when(leadStatusRepository.findByActiveTrueAndIsFollowUpStatusTrueAndIsDeletedFalseOrderByDisplayOrderAsc())
                .thenReturn(List.of(formFollowUpStatus, counselingFollowUpStatus));
        when(leadStatusRepository.save(any(LeadStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDataScope scope = UserDataScope.builder().scopeType(ScopeType.SYSTEM).isAdmin(true).userId(counselor.getId()).build();
        when(userDataScopeService.getScopeForCurrentUser()).thenReturn(scope);
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);

        when(userRepository.findByUsername(counselor.getUsername())).thenReturn(Optional.of(counselor));

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(counselor.getUsername());
        when(auth.getPrincipal()).thenReturn(counselor.getUsername());
        SecurityContext sc = mock(SecurityContext.class);
        when(sc.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(sc);

        when(leadFollowUpRepository.save(any(LeadFollowUp.class))).thenAnswer(inv -> {
            LeadFollowUp fu = inv.getArgument(0);
            fu.setId(UUID.randomUUID());
            return fu;
        });
        when(leadMapper.toDto(any(LeadFollowUp.class))).thenReturn(LeadFollowUpResponse.builder().id(UUID.randomUUID()).build());
    }

    @Test
    @DisplayName("API #1: FollowUp Statuses Dropdown returns valid lifecycle states")
    void test1_GetFollowUpStatusesDropdown() {
        List<FollowUpStatusDropdownResponse> result = dropdownService.getFollowUpStatusesDropdown();

        assertNotNull(result);
        assertEquals(6, result.size());
        assertTrue(result.stream().anyMatch(r -> "PENDING".equals(r.getName()) && "Pending".equals(r.getDisplayName())));
        assertTrue(result.stream().anyMatch(r -> "UPCOMING".equals(r.getName()) && "Upcoming".equals(r.getDisplayName())));
        assertTrue(result.stream().anyMatch(r -> "COMPLETED".equals(r.getName()) && "Completed".equals(r.getDisplayName())));
        assertTrue(result.stream().anyMatch(r -> "CANCELLED".equals(r.getName()) && "Cancelled".equals(r.getDisplayName())));
        assertTrue(result.stream().anyMatch(r -> "MISSED".equals(r.getName()) && "Missed".equals(r.getDisplayName())));
        assertTrue(result.stream().anyMatch(r -> "NOT_CONNECTED".equals(r.getName()) && "Not Connected".equals(r.getDisplayName())));
    }

    @Test
    @DisplayName("API #2: Lead Status Dropdown for Follow-Up Scheduling returns only isFollowUpStatus=true")
    void test2_GetFollowUpLeadStatusesDropdown() {
        List<LeadStatusDropdownResponse> result = dropdownService.getFollowUpLeadStatusesDropdown();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("FORM_FOLLOW_UP", result.get(0).getCode());
        assertTrue(result.get(0).isFollowUpStatus());
        assertEquals("COUNSELING_FOLLOW_UP", result.get(1).getCode());
        assertTrue(result.get(1).isFollowUpStatus());
        // Normal statuses like RAW and REGISTERED must not appear
        assertFalse(result.stream().anyMatch(r -> "RAW".equals(r.getCode())));
        assertFalse(result.stream().anyMatch(r -> "REGISTERED".equals(r.getCode())));
    }

    @Test
    @DisplayName("API #3: Follow-Up Dashboard Status Counts groups leads by follow-up status with zero defaults")
    @SuppressWarnings("unchecked")
    void test3_GetFollowUpStatusCounts_WithZeroDefaults() throws Exception {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<Object[]> cq = mock(CriteriaQuery.class);
        Root<Lead> root = mock(Root.class);
        Path<Object> leadStatusPath = mock(Path.class);
        Path<Object> statusIdPath = mock(Path.class);
        Path<Object> isDeletedPath = mock(Path.class);
        TypedQuery<Object[]> tq = mock(TypedQuery.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(Object[].class)).thenReturn(cq);
        when(cq.from(Lead.class)).thenReturn(root);
        when(root.get("currentStatus")).thenReturn(leadStatusPath);
        when(leadStatusPath.get("id")).thenReturn(statusIdPath);
        when(root.get("isDeleted")).thenReturn(isDeletedPath);
        when(cb.isFalse(any())).thenReturn(mock(Predicate.class));
        when(statusIdPath.in(any(List.class))).thenReturn(mock(Predicate.class));
        when(cb.count(any())).thenReturn(mock(jakarta.persistence.criteria.Expression.class));
        when(cb.and(any())).thenReturn(mock(Predicate.class));
        when(entityManager.createQuery(cq)).thenReturn(tq);

        // Mock database returned count: FORM_FOLLOW_UP has 15 leads, COUNSELING_FOLLOW_UP has 0 leads
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{formFollowUpStatus.getId(), 15L});
        when(tq.getResultList()).thenReturn(rows);

        List<FollowUpStatusCountDTO> counts = followUpService.getFollowUpStatusCounts();

        assertNotNull(counts);
        assertEquals(2, counts.size());

        FollowUpStatusCountDTO formCount = counts.stream().filter(c -> c.getStatusId().equals(formFollowUpStatus.getId())).findFirst().orElse(null);
        assertNotNull(formCount);
        assertEquals("FORM_FOLLOW_UP", formCount.getStatusCode());
        assertEquals(15L, formCount.getCount());

        FollowUpStatusCountDTO counselingCount = counts.stream().filter(c -> c.getStatusId().equals(counselingFollowUpStatus.getId())).findFirst().orElse(null);
        assertNotNull(counselingCount);
        assertEquals("COUNSELING_FOLLOW_UP", counselingCount.getStatusCode());
        assertEquals(0L, counselingCount.getCount()); // Zero-default
    }

    @Test
    @DisplayName("Admin dynamic classification: toggling isFollowUpStatus reflects immediately")
    void test4_AdminToggleFollowUpClassification() {
        // Admin updates registeredStatus to isFollowUpStatus = true
        LeadStatusRequest req = LeadStatusRequest.builder()
                .name("Registered")
                .code("REGISTERED")
                .active(true)
                .isFollowUpStatus(true)
                .build();

        when(leadMapper.toDto(any(LeadStatus.class))).thenAnswer(inv -> {
            LeadStatus s = inv.getArgument(0);
            return LeadStatusResponse.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .code(s.getCode())
                    .isFollowUpStatus(s.isFollowUpStatus())
                    .build();
        });

        LeadStatusResponse updated = leadStatusService.update(registeredStatus.getId(), req);

        assertNotNull(updated);
        assertTrue(registeredStatus.isFollowUpStatus());
    }

    @Test
    @DisplayName("Scheduling validation: scheduling with isFollowUpStatus=true succeeds")
    void test5_ScheduleFollowUp_WithValidFollowUpStatus_Success() throws Exception {
        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .leadId(lead.getId())
                .followUpDate(LocalDateTime.now().plusDays(1))
                .remarks("Scheduled demo call")
                .leadStatusId(formFollowUpStatus.getId())
                .build();

        LeadFollowUpResponse response = leadFollowUpService.createFollowUp(lead.getId(), request);

        assertNotNull(response);
        assertEquals(formFollowUpStatus, lead.getLeadStatus());
    }

    @Test
    @DisplayName("Scheduling validation: scheduling with isFollowUpStatus=false is REJECTED")
    void test6_ScheduleFollowUp_WithNonFollowUpStatus_Rejected() {
        LeadFollowUpRequest request = LeadFollowUpRequest.builder()
                .leadId(lead.getId())
                .followUpDate(LocalDateTime.now().plusDays(1))
                .remarks("Scheduled demo call")
                .leadStatusId(registeredStatus.getId()) // isFollowUpStatus = false
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                leadFollowUpService.createFollowUp(lead.getId(), request));

        assertTrue(ex.getMessage().contains("is not configured as a follow-up status"));
    }
}
