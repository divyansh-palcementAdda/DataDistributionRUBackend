package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
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

import com.app.datadistribution.dto.useractivity.UserDailyActivityResponseDTO;
import com.app.datadistribution.dto.useractivity.UserInactivityPeriodDTO;
import com.app.datadistribution.dto.useractivity.UserSessionHistoryDTO;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.entity.UserActivityPeriod;
import com.app.datadistribution.entity.UserLoginSession;
import com.app.datadistribution.enums.ActivityPeriodType;
import com.app.datadistribution.enums.LogoutReason;
import com.app.datadistribution.enums.SessionStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.LeadAvailedRepository;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserActivityPeriodRepository;
import com.app.datadistribution.repository.UserLoginSessionRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.UserActivityServiceImpl;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
public class UserActivityServiceTest {

    @Mock
    private UserLoginSessionRepository sessionRepository;

    @Mock
    private UserActivityPeriodRepository periodRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadAvailedRepository availedRepository;

    @Mock
    private LeadFollowUpRepository followUpRepository;

    @Mock
    private LeadStatusRepository leadStatusRepository;

    @Mock
    private IUserDataScopeService dataScopeService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private CriteriaBuilder criteriaBuilder;

    @Mock
    private CriteriaQuery<Long> countQuery;

    @Mock
    private Root<?> root;

    @Mock
    private TypedQuery<Long> typedQuery;

    @InjectMocks
    private UserActivityServiceImpl userActivityService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .firstName("John")
                .lastName("Doe")
                .username("johndoe")
                .email("john@example.com")
                .active(true)
                .build();
        testUser.setId(userId);
    }

    @Test
    @DisplayName("recordLogin closes stale sessions and creates new session")
    void testRecordLogin() {
        when(sessionRepository.findActiveSessionsForUser(any(), any())).thenReturn(Collections.emptyList());
        when(sessionRepository.save(any(UserLoginSession.class))).thenAnswer(i -> {
            UserLoginSession s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        UserLoginSession session = userActivityService.recordLogin(testUser, "127.0.0.1", "Chrome", 1L);
        assertNotNull(session);
        assertEquals(SessionStatus.ACTIVE, session.getSessionStatus());
        verify(periodRepository).save(any(UserActivityPeriod.class));
    }

    @Test
    @DisplayName("recordHeartbeat updates lastActivityAt and handles inactivity resumption")
    void testRecordHeartbeat() {
        UserLoginSession activeSession = UserLoginSession.builder()
                .user(testUser)
                .loginAt(LocalDateTime.now().minusHours(1))
                .lastActivityAt(LocalDateTime.now().minusMinutes(3)) // 3 min ago (> 2 min gap)
                .sessionStatus(SessionStatus.ACTIVE)
                .build();
        activeSession.setId(UUID.randomUUID());

        when(sessionRepository.findFirstActiveSessionByUserId(userId)).thenReturn(Optional.of(activeSession));
        when(periodRepository.findOpenPeriodForSession(any())).thenReturn(Optional.empty());

        userActivityService.recordHeartbeat(userId);

        verify(sessionRepository).save(activeSession);
        verify(periodRepository, times(2)).save(any(UserActivityPeriod.class));
    }

    @Test
    @DisplayName("recordLogout closes session and marks status LOGGED_OUT")
    void testRecordLogout() {
        UserLoginSession activeSession = UserLoginSession.builder()
                .user(testUser)
                .loginAt(LocalDateTime.now().minusHours(2))
                .lastActivityAt(LocalDateTime.now().minusMinutes(1))
                .sessionStatus(SessionStatus.ACTIVE)
                .build();
        activeSession.setId(UUID.randomUUID());

        when(sessionRepository.findActiveSessionsForUser(any(), any())).thenReturn(List.of(activeSession));
        when(periodRepository.findOpenPeriodForSession(any())).thenReturn(Optional.empty());

        userActivityService.recordLogout(userId, "sample-token", LogoutReason.MANUAL_LOGOUT);

        assertEquals(SessionStatus.LOGGED_OUT, activeSession.getSessionStatus());
        assertEquals(LogoutReason.MANUAL_LOGOUT, activeSession.getLogoutReason());
        assertNotNull(activeSession.getLogoutAt());
        verify(sessionRepository).save(activeSession);
    }

    @Test
    @DisplayName("getSessionHistory returns mapped session DTOs")
    void testGetSessionHistory() throws Exception {
        setupSystemScope();

        UserLoginSession session = UserLoginSession.builder()
                .user(testUser)
                .loginAt(LocalDateTime.now().minusHours(3))
                .logoutAt(LocalDateTime.now().minusHours(1))
                .lastActivityAt(LocalDateTime.now().minusHours(1))
                .sessionStatus(SessionStatus.LOGGED_OUT)
                .logoutReason(LogoutReason.MANUAL_LOGOUT)
                .totalInactiveDurationSeconds(600L)
                .build();
        session.setId(UUID.randomUUID());

        when(sessionRepository.findSessionsForUserOnDate(any(), any(), any())).thenReturn(List.of(session));

        List<UserSessionHistoryDTO> result = userActivityService.getSessionHistory(userId, LocalDate.now());
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("MANUAL_LOGOUT", result.get(0).getLogoutReason());
        assertEquals(10, result.get(0).getInactiveDurationMinutes());
    }

    @Test
    @DisplayName("getInactivityHistory returns mapped inactivity period DTOs")
    void testGetInactivityHistory() throws Exception {
        setupSystemScope();

        UserActivityPeriod period = UserActivityPeriod.builder()
                .user(testUser)
                .periodType(ActivityPeriodType.INACTIVE)
                .startedAt(LocalDateTime.now().minusMinutes(40))
                .endedAt(LocalDateTime.now().minusMinutes(25))
                .durationSeconds(900L)
                .reason("BROWSER_INACTIVITY")
                .build();
        period.setId(UUID.randomUUID());

        when(periodRepository.findPeriodsForUserOnDateByType(any(), any(), any(), any())).thenReturn(List.of(period));

        List<UserInactivityPeriodDTO> result = userActivityService.getInactivityHistory(userId, LocalDate.now());
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(15, result.get(0).getDurationMinutes());
        assertEquals("BROWSER_INACTIVITY", result.get(0).getReason());
    }

    private void setupSystemScope() throws UnauthorizedException, BadRequestException {
        org.mockito.Mockito.lenient().when(dataScopeService.getScopeForCurrentUser()).thenReturn(
                UserDataScope.builder().scopeType(ScopeType.SYSTEM).userId(UUID.randomUUID()).isAdmin(true).build());
    }
}
