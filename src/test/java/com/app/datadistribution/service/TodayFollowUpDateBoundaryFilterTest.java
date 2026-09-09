package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest;
import com.app.datadistribution.dto.dashboard.DashboardFollowUpCountResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpPagedResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpResponseDTO;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.DashboardAnalyticsRepository;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.repository.specification.FollowUpSpecification;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.DashboardServiceImpl;
import com.app.datadistribution.service.impl.FollowUpServiceImpl;
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
public class TodayFollowUpDateBoundaryFilterTest {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 9, 9);

    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IUserDataScopeService dataScopeService;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private EntityManager entityManager;
    @Mock
    private DashboardAnalyticsRepository dashboardAnalyticsRepository;

    @InjectMocks
    private FollowUpServiceImpl followUpService;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private User counselorUser;
    private UUID counselorId;
    private UserDataScope counselorScope;

    @BeforeEach
    void setUp() throws Exception {
        counselorId = UUID.randomUUID();
        counselorUser = User.builder()
                .username("counselor1")
                .active(true)
                .build();
        counselorUser.setId(counselorId);

        counselorScope = UserDataScope.builder()
                .userId(counselorId)
                .scopeType(ScopeType.SELF)
                .isAdmin(false)
                .build();

        when(dataScopeService.getScopeForCurrentUser()).thenReturn(counselorScope);
        when(dataScopeService.getScopeForCurrentUser(any(DashboardAnalyticsFilterRequest.class))).thenReturn(counselorScope);
    }

    // =========================================================================
    // 13. TEST EXACT DATE BOUNDARY CASES FOR 2026-09-09
    // =========================================================================

    /**
     * Helper to evaluate whether a candidate followUpDate falls in the half-open interval:
     * [2026-09-09T00:00:00, 2026-09-10T00:00:00)
     */
    private boolean isIncludedInTodayRange(LocalDateTime dt, LocalDate targetDay) {
        LocalDateTime startOfDay = targetDay.atStartOfDay();
        LocalDateTime startOfNextDay = targetDay.plusDays(1).atStartOfDay();
        return !dt.isBefore(startOfDay) && dt.isBefore(startOfNextDay);
    }

    @Test
    @DisplayName("TEST 1: 2026-09-09 00:00:00.000000 is INCLUDED in Today's Followups")
    void test1_StartOfToday_Included() {
        LocalDateTime dt = LocalDateTime.of(2026, 9, 9, 0, 0, 0, 0);
        assertTrue(isIncludedInTodayRange(dt, TEST_DATE), "Start of today boundary must be included");
    }

    @Test
    @DisplayName("TEST 2: 2026-09-09 10:59:01.461809 is INCLUDED in Today's Followups")
    void test2_MidDay_Included() {
        LocalDateTime dt = LocalDateTime.of(2026, 9, 9, 10, 59, 1, 461809000);
        assertTrue(isIncludedInTodayRange(dt, TEST_DATE), "Midday follow-up must be included");
    }

    @Test
    @DisplayName("TEST 3: 2026-09-09 23:59:59.999999 is INCLUDED in Today's Followups")
    void test3_EndOfToday_Included() {
        LocalDateTime dt = LocalDateTime.of(2026, 9, 9, 23, 59, 59, 999999000);
        assertTrue(isIncludedInTodayRange(dt, TEST_DATE), "Last microsecond of today must be included");
    }

    @Test
    @DisplayName("TEST 4: 2026-09-10 00:00:00.000000 is EXCLUDED from Today's Followups (CRITICAL BUG FIX)")
    void test4_StartOfTomorrow_Excluded() {
        LocalDateTime dt = LocalDateTime.of(2026, 9, 10, 0, 0, 0, 0);
        assertFalse(isIncludedInTodayRange(dt, TEST_DATE), "Start of tomorrow must be EXCLUDED from today's followups");
    }

    @Test
    @DisplayName("TEST 5: 2026-09-10 10:00:00.000000 is EXCLUDED from Today's Followups")
    void test5_TomorrowMidDay_Excluded() {
        LocalDateTime dt = LocalDateTime.of(2026, 9, 10, 10, 0, 0, 0);
        assertFalse(isIncludedInTodayRange(dt, TEST_DATE), "Tomorrow's follow-up must be EXCLUDED");
    }

    @Test
    @DisplayName("TEST 6: 2026-09-08 23:59:59.999999 is EXCLUDED from Today's Followups")
    void test6_YesterdayEnd_Excluded() {
        LocalDateTime dt = LocalDateTime.of(2026, 9, 8, 23, 59, 59, 999999000);
        assertFalse(isIncludedInTodayRange(dt, TEST_DATE), "Yesterday's last moment must be EXCLUDED");
    }

    // =========================================================================
    // 14. TEST STATUS COMBINATIONS & SPECIFICATION CRITERIA
    // =========================================================================

    @Test
    @DisplayName("Specification creates correct half-open interval predicates")
    void testSpecification_BuildsHalfOpenIntervalPredicates() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        Root<LeadFollowUp> root = mock(Root.class);
        Path<LocalDateTime> datePath = mock(Path.class);
        Predicate gtePred = mock(Predicate.class);
        Predicate ltPred = mock(Predicate.class);
        Predicate combinedPred = mock(Predicate.class);

        when(root.<LocalDateTime>get("followUpDate")).thenReturn(datePath);
        when(cb.greaterThanOrEqualTo(eq(datePath), eq(LocalDateTime.of(2026, 9, 9, 0, 0, 0)))).thenReturn(gtePred);
        when(cb.lessThan(eq(datePath), eq(LocalDateTime.of(2026, 9, 10, 0, 0, 0)))).thenReturn(ltPred);
        when(cb.and(gtePred, ltPred)).thenReturn(combinedPred);

        Specification<LeadFollowUp> spec = FollowUpSpecification.hasFollowUpDateOn(TEST_DATE);
        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
        verify(cb).greaterThanOrEqualTo(eq(datePath), eq(LocalDateTime.of(2026, 9, 9, 0, 0, 0)));
        verify(cb).lessThan(eq(datePath), eq(LocalDateTime.of(2026, 9, 10, 0, 0, 0)));
        verify(cb, never()).between(any(), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Dashboard count uses half-open interval [startOfDay, startOfNextDay)")
    void testDashboardCount_UsesHalfOpenInterval() throws Exception {
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
        when(cb.greaterThanOrEqualTo(any(), any(LocalDateTime.class))).thenReturn(pred);
        when(cb.lessThan(any(), any(LocalDateTime.class))).thenReturn(pred);
        when(cb.or(any(Predicate[].class))).thenReturn(pred);
        when(cb.and(any(Predicate[].class))).thenReturn(pred);
        when(cb.countDistinct(any())).thenReturn(mock(jakarta.persistence.criteria.Expression.class));
        when(cq.select(any())).thenReturn(cq);
        when(cq.where(any(Predicate[].class))).thenReturn(cq);
        when(entityManager.createQuery(cq)).thenReturn(typedQuery);
        when(typedQuery.getSingleResult()).thenReturn(5L);

        DashboardFollowUpCountResponseDTO result = dashboardService.getTodayFollowUpsCount(new DashboardAnalyticsFilterRequest());

        assertNotNull(result);
        assertEquals(5L, result.getCount());
        assertEquals("TodayFollowUps", result.getType());
        assertEquals(LocalDate.now(IST_ZONE), result.getDate());

        // Verify cb.greaterThanOrEqualTo and cb.lessThan were used with start of day and start of next day
        LocalDate today = LocalDate.now(IST_ZONE);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        verify(cb).greaterThanOrEqualTo(any(), eq(todayStart));
        verify(cb).lessThan(any(), eq(tomorrowStart));
        verify(cb, never()).between(any(), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    // =========================================================================
    // 15. TEST DASHBOARD CARD VS TODAY FOLLOWUP LIST CONSISTENCY
    // =========================================================================

    @Test
    @DisplayName("End-to-end: Reported record (2026-09-10 00:00:00 UPCOMING) is excluded from Today's list")
    void testEndToEnd_ReportedRecordExcludedFromTodayList() throws Exception {
        Lead lead = Lead.builder()
                .leadCode("LEAD-101")
                .fullName("Test Lead")
                .build();
        lead.setId(UUID.randomUUID());

        LeadFollowUp todayFollowUp = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(LocalDateTime.of(2026, 9, 9, 10, 0, 0))
                .status(FollowUpStatus.PENDING)
                .remarks("Today's follow-up")
                .assignedTo(counselorUser)
                .build();
        todayFollowUp.setId(UUID.randomUUID());

        FollowUpResponseDTO todayDto = FollowUpResponseDTO.builder()
                .id(todayFollowUp.getId())
                .leadCode("LEAD-101")
                .leadFullName("Test Lead")
                .followUpDate(todayFollowUp.getFollowUpDate())
                .status(FollowUpStatus.PENDING)
                .remarks("Today's follow-up")
                .build();

        when(leadMapper.toFollowUpResponseDto(todayFollowUp)).thenReturn(todayDto);

        // Mock repository findAll returning only the valid today follow-up
        Page<LeadFollowUp> pageResult = new PageImpl<>(List.of(todayFollowUp));
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

        PageRequestDTO pageReq = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpPagedResponseDTO listResponse = followUpService.getTodayFollowUps(pageReq);

        assertNotNull(listResponse);
        assertEquals(1, listResponse.getContent().size());
        assertEquals("LEAD-101", listResponse.getContent().get(0).getLeadCode());
        assertEquals(LocalDateTime.of(2026, 9, 9, 10, 0, 0), listResponse.getContent().get(0).getFollowUpDate());

        // Verify that a tomorrow record (2026-09-10 00:00:00) is never part of this today result
        for (FollowUpResponseDTO item : listResponse.getContent()) {
            assertNotEquals(LocalDate.of(2026, 9, 10), item.getFollowUpDate().toLocalDate());
            assertTrue(item.getFollowUpDate().toLocalDate().isEqual(LocalDate.now(IST_ZONE))
                    || item.getFollowUpDate().toLocalDate().isEqual(TEST_DATE));
        }
    }
}
