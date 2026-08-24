package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.dashboard.FollowUpUserNotLoggedInPageResponseDTO;
import com.app.datadistribution.dto.dashboard.UserNotLoggedInPageResponseDTO;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.ActivityLogRepository;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.impl.DashboardServiceImpl;

@ExtendWith(MockitoExtension.class)
class UserLoginDashboardCardsTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;
    @Mock
    private com.app.datadistribution.service.interfaces.IUserDataScopeService dataScopeService;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private User adminUser;
    private User userLoggedInEarly;
    private User userLoggedInLate;
    private User userNotLoggedInWithFollowUp;
    private User userNotLoggedInNoFollowUp;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(dashboardService, "cutoffTimeStr", "11:00");

        Role adminRole = Role.builder().name(RoleType.SUPER_ADMIN.name()).build();
        adminUser = User.builder().username("admin").active(true).roles(Set.of(adminRole)).build();
        adminUser.setId(UUID.randomUUID());

        userLoggedInEarly = User.builder().username("early").firstName("Early").lastName("User").email("early@test.com").active(true).build();
        userLoggedInEarly.setId(UUID.randomUUID());

        userLoggedInLate = User.builder().username("late").firstName("Late").lastName("User").email("late@test.com").active(true).build();
        userLoggedInLate.setId(UUID.randomUUID());

        userNotLoggedInWithFollowUp = User.builder().username("nologin_fu").firstName("NoLogin").lastName("FollowUp").email("nologin_fu@test.com").active(true).build();
        userNotLoggedInWithFollowUp.setId(UUID.randomUUID());

        userNotLoggedInNoFollowUp = User.builder().username("nologin_nofu").firstName("NoLogin").lastName("NoFollowUp").email("nologin_nofu@test.com").active(true).build();
        userNotLoggedInNoFollowUp.setId(UUID.randomUUID());

        com.app.datadistribution.service.dto.UserDataScope systemScope = com.app.datadistribution.service.dto.UserDataScope.builder()
                .scopeType(com.app.datadistribution.service.dto.UserDataScope.ScopeType.SYSTEM)
                .isAdmin(true)
                .userId(adminUser.getId())
                .currentUser(adminUser)
                .build();
        lenient().when(dataScopeService.getScopeForCurrentUser()).thenReturn(systemScope);
        lenient().when(dataScopeService.getScopeForCurrentUser((String) any())).thenReturn(systemScope);
        lenient().when(dataScopeService.getScopeForCurrentUser(any(com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest.class))).thenReturn(systemScope);

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("admin");
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
    }

    @Test
    void testUsersNotLoggedInToday_FiltersCorrectly() throws UnauthorizedException, BadRequestException {
        lenient().when(userRepository.findAll()).thenReturn(List.of(userLoggedInEarly, userNotLoggedInWithFollowUp, userNotLoggedInNoFollowUp));

        ZonedDateTime nowIST = ZonedDateTime.now(IST);
        LocalDateTime startOfDay = nowIST.toLocalDate().atStartOfDay();

        // early user logged in at 09:30 AM
        List<Object[]> dailyLoginStats = new ArrayList<>();
        dailyLoginStats.add(new Object[]{userLoggedInEarly.getEmail().toLowerCase(), startOfDay.plusHours(9).plusMinutes(30), startOfDay.plusHours(9).plusMinutes(30)});
        lenient().when(activityLogRepository.findDailyLoginStatsGroupedByPerformedBy(any(), any())).thenReturn(dailyLoginStats);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).sortBy("name").sortDirection("ASC").build();
        UserNotLoggedInPageResponseDTO response = dashboardService.getUsersNotLoggedInToday(pageRequest);

        assertNotNull(response);
        assertEquals(2, response.getTotalElements());
        assertTrue(response.getContent().stream().anyMatch(u -> u.getUserId().equals(userNotLoggedInWithFollowUp.getId())));
        assertTrue(response.getContent().stream().anyMatch(u -> u.getUserId().equals(userNotLoggedInNoFollowUp.getId())));
        assertFalse(response.getContent().stream().anyMatch(u -> u.getUserId().equals(userLoggedInEarly.getId())));
    }

    @Test
    void testFollowUpUsersNotLoggedInBy11Am_FiltersCorrectlyAtOrAfterCutoff() throws UnauthorizedException, BadRequestException {
        lenient().when(userRepository.findAll()).thenReturn(List.of(userLoggedInEarly, userLoggedInLate, userNotLoggedInWithFollowUp, userNotLoggedInNoFollowUp));

        ZonedDateTime nowIST = ZonedDateTime.now(IST);
        LocalDateTime startOfDay = nowIST.toLocalDate().atStartOfDay();

        List<Object[]> followUpCounts = new ArrayList<>();
        followUpCounts.add(new Object[]{userLoggedInEarly.getId(), 2L});
        followUpCounts.add(new Object[]{userLoggedInLate.getId(), 3L});
        followUpCounts.add(new Object[]{userNotLoggedInWithFollowUp.getId(), 4L});
        lenient().when(leadFollowUpRepository.countScheduledFollowUpsGroupedByUserBetween(any(), any())).thenReturn(followUpCounts);
        lenient().when(leadFollowUpRepository.findEarliestScheduledFollowUpGroupedByUserBetween(any(), any())).thenReturn(new ArrayList<>());

        List<Object[]> dailyLoginStats = new ArrayList<>();
        dailyLoginStats.add(new Object[]{userLoggedInEarly.getEmail().toLowerCase(), startOfDay.plusHours(10), startOfDay.plusHours(10)});
        dailyLoginStats.add(new Object[]{userLoggedInLate.getEmail().toLowerCase(), startOfDay.plusHours(11).plusMinutes(15), startOfDay.plusHours(11).plusMinutes(15)});
        lenient().when(activityLogRepository.findDailyLoginStatsGroupedByPerformedBy(any(), any())).thenReturn(dailyLoginStats);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).sortBy("todayFollowUpCount").sortDirection("DESC").build();
        FollowUpUserNotLoggedInPageResponseDTO response = dashboardService.getFollowUpUsersNotLoggedInBy11Am(pageRequest);

        assertNotNull(response);

        // If current time IST is before 11:00 AM, returns 0 elements (time-aware rule)
        // If current time IST is at/after 11:00 AM, returns 2 elements (userLoggedInLate and userNotLoggedInWithFollowUp)
        if (nowIST.toLocalTime().isBefore(LocalTime.of(11, 0))) {
            assertEquals(0, response.getTotalElements());
        } else {
            assertEquals(2, response.getTotalElements());
            assertTrue(response.getContent().stream().anyMatch(u -> u.getUserId().equals(userLoggedInLate.getId())));
            assertTrue(response.getContent().stream().anyMatch(u -> u.getUserId().equals(userNotLoggedInWithFollowUp.getId())));
            assertFalse(response.getContent().stream().anyMatch(u -> u.getUserId().equals(userLoggedInEarly.getId())));
        }
    }
}
