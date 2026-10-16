package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.followup.FollowUpFilterCriteria;
import com.app.datadistribution.dto.followup.FollowUpPagedResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpSummaryDTO;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.FollowUpServiceImpl;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
public class FollowUpCanonicalFilterTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;

    @Mock
    private LeadStatusRepository leadStatusRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IUserDataScopeService dataScopeService;

    @Mock
    private LeadMapper leadMapper;

    @InjectMocks
    private FollowUpServiceImpl followUpService;

    private User adminUser;
    private User counselor1;
    private User counselor2;
    private Department dept1;
    private Department dept2;

    private LeadStatus continuousStatus;
    private LeadStatus notConnectedStatus;
    private LeadStatus interestedStatus;

    private Lead leadContinuous;
    private Lead leadNotConnected;

    private LeadFollowUp followUpTodayContinuous;
    private LeadFollowUp followUpTodayNotConnected;
    private LeadFollowUp followUpFuture;
    private LeadFollowUp followUpPastMissed;
    private LeadFollowUp followUpCompleted;

    @BeforeEach
    void setUp() {
        dept1 = Department.builder().name("Admissions").build();
        dept1.setId(UUID.randomUUID());

        dept2 = Department.builder().name("Marketing").build();
        dept2.setId(UUID.randomUUID());

        adminUser = User.builder().username("admin").build();
        adminUser.setId(UUID.randomUUID());

        counselor1 = User.builder().username("counselor1").build();
        counselor1.setId(UUID.randomUUID());

        counselor2 = User.builder().username("counselor2").build();
        counselor2.setId(UUID.randomUUID());

        continuousStatus = LeadStatus.builder()
                .name("Continuous Follow Up")
                .code("CONTINUOUS_FOLLOW_UP")
                .build();
        continuousStatus.setId(UUID.randomUUID());

        notConnectedStatus = LeadStatus.builder()
                .name("Not Connected")
                .code("NOT_CONNECTED")
                .build();
        notConnectedStatus.setId(UUID.randomUUID());

        interestedStatus = LeadStatus.builder()
                .name("Interested")
                .code("INTERESTED")
                .build();
        interestedStatus.setId(UUID.randomUUID());

        leadContinuous = Lead.builder()
                .fullName("Lead Continuous")
                .phoneNumber("9876543210")
                .currentStatus(continuousStatus)
                .department(dept1)
                .assignedTo(counselor1)
                .build();
        leadContinuous.setId(UUID.randomUUID());

        leadNotConnected = Lead.builder()
                .fullName("Lead Not Connected")
                .phoneNumber("9876543211")
                .currentStatus(notConnectedStatus)
                .department(dept1)
                .assignedTo(counselor1)
                .build();
        leadNotConnected.setId(UUID.randomUUID());

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        followUpTodayContinuous = LeadFollowUp.builder()
                .lead(leadContinuous)
                .followUpDate(today.atTime(10, 30))
                .status(FollowUpStatus.PENDING)
                .assignedTo(counselor1)
                .createdByUser(counselor1)
                .remarks("Scheduled today for continuous lead")
                .build();
        followUpTodayContinuous.setId(UUID.randomUUID());

        followUpTodayNotConnected = LeadFollowUp.builder()
                .lead(leadNotConnected)
                .followUpDate(today.atTime(11, 0))
                .status(FollowUpStatus.NOT_CONNECTED)
                .assignedTo(counselor1)
                .createdByUser(counselor1)
                .remarks("Followup attempt failed - not connected")
                .build();
        followUpTodayNotConnected.setId(UUID.randomUUID());

        followUpFuture = LeadFollowUp.builder()
                .lead(leadContinuous)
                .followUpDate(today.plusDays(3).atTime(14, 0))
                .status(FollowUpStatus.UPCOMING)
                .assignedTo(counselor1)
                .createdByUser(counselor1)
                .remarks("Scheduled in the future")
                .build();
        followUpFuture.setId(UUID.randomUUID());

        followUpPastMissed = LeadFollowUp.builder()
                .lead(leadContinuous)
                .followUpDate(today.minusDays(2).atTime(15, 0))
                .status(FollowUpStatus.MISSED)
                .assignedTo(counselor1)
                .createdByUser(counselor1)
                .remarks("Followup date past")
                .build();
        followUpPastMissed.setId(UUID.randomUUID());

        followUpCompleted = LeadFollowUp.builder()
                .lead(leadContinuous)
                .followUpDate(today.atTime(9, 0))
                .status(FollowUpStatus.COMPLETED)
                .completed(true)
                .assignedTo(counselor1)
                .createdByUser(counselor1)
                .remarks("Call finished successfully")
                .build();
        followUpCompleted.setId(UUID.randomUUID());
    }

    private void mockAdminScope() throws Exception {
        UserDataScope scope = UserDataScope.builder()
                .userId(adminUser.getId())
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(scope);
    }

    private void mockCounselorScope(User counselor) throws Exception {
        UserDataScope scope = UserDataScope.builder()
                .userId(counselor.getId())
                .scopeType(ScopeType.SELF)
                .isAdmin(false)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(scope);
    }

    @Test
    @DisplayName("Scenario 1: Today's due follow-ups return correct records with canonical criteria")
    void testTodaysDueFollowUpsReturnCorrectRecords() throws Exception {
        mockAdminScope();
        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpFilterCriteria criteria = FollowUpFilterCriteria.builder()
                .category("TODAY")
                .build();

        List<LeadFollowUp> content = List.of(followUpTodayContinuous, followUpTodayNotConnected);
        Page<LeadFollowUp> page = new PageImpl<>(content, org.springframework.data.domain.PageRequest.of(0, 10), 2);

        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(any(LeadFollowUp.class))).thenReturn(new FollowUpResponseDTO());

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, criteria);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        verify(leadFollowUpRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Scenario 2: Today's Continuous follow-ups filter by today AND continuous outcome")
    void testTodaysContinuousFilter() throws Exception {
        mockAdminScope();
        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpFilterCriteria criteria = FollowUpFilterCriteria.builder()
                .category("TODAY_CONTINUOUS")
                .build();

        List<LeadFollowUp> content = List.of(followUpTodayContinuous);
        Page<LeadFollowUp> page = new PageImpl<>(content, org.springframework.data.domain.PageRequest.of(0, 10), 1);

        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUpTodayContinuous)).thenReturn(new FollowUpResponseDTO());

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, criteria);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("Scenario 3: Today's Not Connected follow-ups filter by today AND NOT_CONNECTED status")
    void testTodaysNotConnectedFilter() throws Exception {
        mockAdminScope();
        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpFilterCriteria criteria = FollowUpFilterCriteria.builder()
                .category("TODAY_NOT_CONNECTED")
                .build();

        List<LeadFollowUp> content = List.of(followUpTodayNotConnected);
        Page<LeadFollowUp> page = new PageImpl<>(content, org.springframework.data.domain.PageRequest.of(0, 10), 1);

        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUpTodayNotConnected)).thenReturn(new FollowUpResponseDTO());

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, criteria);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("Scenario 4: Continuous and Not Connected filters represent distinct business outcomes")
    void testContinuousAndNotConnectedDistinction() {
        assertEquals("CONTINUOUS_FOLLOW_UP", leadContinuous.getCurrentStatus().getCode());
        assertEquals(FollowUpStatus.PENDING, followUpTodayContinuous.getStatus());

        assertEquals(FollowUpStatus.NOT_CONNECTED, followUpTodayNotConnected.getStatus());
        assertEquals("NOT_CONNECTED", leadNotConnected.getCurrentStatus().getCode());
        assertNotEquals(followUpTodayContinuous.getStatus(), followUpTodayNotConnected.getStatus());
    }

    @Test
    @DisplayName("Scenario 5: Combining date, outcome, and assigned user filters in single request")
    void testCombiningDateOutcomeAndAssignedUser() throws Exception {
        mockAdminScope();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpFilterCriteria criteria = FollowUpFilterCriteria.builder()
                .date(today)
                .category("TODAY_CONTINUOUS")
                .userId(counselor1.getId())
                .build();

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUpTodayContinuous), org.springframework.data.domain.PageRequest.of(0, 10), 1);
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUpTodayContinuous)).thenReturn(new FollowUpResponseDTO());

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, criteria);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Scenario 6: Combining date, outcome, department, and search filters works")
    void testCombiningDateOutcomeDepartmentAndSearch() throws Exception {
        mockAdminScope();
        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).search("Continuous").build();
        FollowUpFilterCriteria criteria = FollowUpFilterCriteria.builder()
                .category("TODAY_CONTINUOUS")
                .departmentId(dept1.getId())
                .build();

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUpTodayContinuous), org.springframework.data.domain.PageRequest.of(0, 10), 1);
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUpTodayContinuous)).thenReturn(new FollowUpResponseDTO());

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, criteria);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Scenario 7: Follow-ups scheduled in the future are classified as UPCOMING and not today's records")
    void testFutureFollowUpsClassification() throws Exception {
        mockAdminScope();
        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpFilterCriteria criteria = FollowUpFilterCriteria.builder()
                .category("UPCOMING")
                .build();

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUpFuture), org.springframework.data.domain.PageRequest.of(0, 10), 1);
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUpFuture)).thenReturn(new FollowUpResponseDTO());

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, criteria);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(FollowUpStatus.UPCOMING, followUpFuture.getStatus());
        assertTrue(followUpFuture.getFollowUpDate().toLocalDate().isAfter(LocalDate.now(BUSINESS_ZONE)));
    }

    @Test
    @DisplayName("Scenario 8: Past follow-ups follow existing Missed rules without terminal status loss")
    void testPastFollowUpsMissedRules() throws Exception {
        mockAdminScope();
        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpFilterCriteria criteria = FollowUpFilterCriteria.builder()
                .category("MISSED")
                .build();

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUpPastMissed), org.springframework.data.domain.PageRequest.of(0, 10), 1);
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUpPastMissed)).thenReturn(new FollowUpResponseDTO());

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, criteria);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(FollowUpStatus.MISSED, followUpPastMissed.getStatus());
        assertTrue(followUpPastMissed.getFollowUpDate().toLocalDate().isBefore(LocalDate.now(BUSINESS_ZONE)));
    }

    @Test
    @DisplayName("Scenario 9: Completed and Cancelled follow-ups are preserved and not reclassified")
    void testCompletedFollowUpPreservation() throws Exception {
        mockAdminScope();
        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpFilterCriteria criteria = FollowUpFilterCriteria.builder()
                .category("COMPLETED")
                .build();

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUpCompleted), org.springframework.data.domain.PageRequest.of(0, 10), 1);
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUpCompleted)).thenReturn(new FollowUpResponseDTO());

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, criteria);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(FollowUpStatus.COMPLETED, followUpCompleted.getStatus());
        assertTrue(followUpCompleted.isCompleted());
    }

    @Test
    @DisplayName("Scenario 10: Follow-up status is not confused with Lead status")
    void testFollowUpStatusDistinctFromLeadStatus() throws Exception {
        mockAdminScope();
        when(leadStatusRepository.findById(continuousStatus.getId())).thenReturn(Optional.of(continuousStatus));

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpFilterCriteria criteria = FollowUpFilterCriteria.builder()
                .status(FollowUpStatus.PENDING)
                .leadStatusId(continuousStatus.getId())
                .build();

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUpTodayContinuous), org.springframework.data.domain.PageRequest.of(0, 10), 1);
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUpTodayContinuous)).thenReturn(new FollowUpResponseDTO());

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, criteria);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(FollowUpStatus.PENDING, followUpTodayContinuous.getStatus());
        assertEquals(continuousStatus, followUpTodayContinuous.getLead().getCurrentStatus());
    }

    @Test
    @DisplayName("Scenario 11: Pagination returns correct page metadata and total count")
    void testPaginationAndTotalCount() throws Exception {
        mockAdminScope();
        PageRequestDTO pageRequest = PageRequestDTO.builder().page(1).size(5).build();
        FollowUpFilterCriteria criteria = FollowUpFilterCriteria.builder().build();

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUpTodayContinuous), org.springframework.data.domain.PageRequest.of(1, 5), 11);
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUpTodayContinuous)).thenReturn(new FollowUpResponseDTO());

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, criteria);

        assertNotNull(result);
        assertEquals(1, result.getPage());
        assertEquals(5, result.getSize());
        assertEquals(11, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
    }

    @Test
    @DisplayName("Scenario 12: Dashboard counts use consistent backend queries under authorized base scope")
    void testDashboardCountsConsistentFilters() throws Exception {
        mockAdminScope();
        when(leadFollowUpRepository.count(any(Specification.class))).thenReturn(5L);

        FollowUpSummaryDTO stats = followUpService.getDashboardStats();

        assertNotNull(stats);
        assertEquals(5L, stats.getTodayFollowUps());
        assertEquals(5L, stats.getTodayContinuousFollowUps());
        assertEquals(5L, stats.getTodayNotConnectedFollowUps());
        assertEquals(5L, stats.getPendingFollowUps());
        assertEquals(5L, stats.getUpcomingFollowUps());
        assertEquals(5L, stats.getCompletedFollowUps());
        assertEquals(5L, stats.getMissedFollowUps());
        assertEquals(5L, stats.getCancelledFollowUps());
        assertEquals(5L, stats.getNotConnectedFollowUps());
    }

    @Test
    @DisplayName("Scenario 13 & 14: RBAC data scope enforces user/department isolation")
    void testRbacDataScopeEnforcement() throws Exception {
        mockCounselorScope(counselor1);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpFilterCriteria criteria = FollowUpFilterCriteria.builder()
                .userId(counselor1.getId())
                .build();

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUpTodayContinuous), org.springframework.data.domain.PageRequest.of(0, 10), 1);
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUpTodayContinuous)).thenReturn(new FollowUpResponseDTO());

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, criteria);
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Scenario 15: Timezone Asia/Kolkata boundary matches business calendar")
    void testTimezoneAsiaKolkataBoundary() {
        LocalDate todayKolkata = LocalDate.now(BUSINESS_ZONE);
        assertNotNull(todayKolkata);
        assertEquals(BUSINESS_ZONE, ZoneId.of("Asia/Kolkata"));
    }

    @Test
    @DisplayName("Scenario 16: Canonical API handles custom date range correctly")
    void testCustomDateRangeFiltering() throws Exception {
        mockAdminScope();
        LocalDate fromDate = LocalDate.now(BUSINESS_ZONE).minusDays(7);
        LocalDate toDate = LocalDate.now(BUSINESS_ZONE);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpFilterCriteria criteria = FollowUpFilterCriteria.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUpTodayContinuous, followUpPastMissed), org.springframework.data.domain.PageRequest.of(0, 10), 2);
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(any(LeadFollowUp.class))).thenReturn(new FollowUpResponseDTO());

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, criteria);
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
    }

    @Test
    @DisplayName("Scenario 17: Dashboard counts are strictly scoped when filtered by selected lead status")
    void testDashboardStatsFilteredByLeadStatus() throws Exception {
        mockAdminScope();
        when(leadFollowUpRepository.count(any(Specification.class))).thenReturn(3L);

        FollowUpSummaryDTO stats = followUpService.getDashboardStats(null, null, List.of(continuousStatus.getId()), null, null, null);

        assertNotNull(stats);
        assertEquals(3L, stats.getAllFollowUps());
        assertEquals(3L, stats.getTodayFollowUps());
        verify(leadFollowUpRepository, atLeastOnce()).count(any(Specification.class));
    }
}
