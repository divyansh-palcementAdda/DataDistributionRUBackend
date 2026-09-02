package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.useractivity.UserDailyActivityResponseDTO;
import com.app.datadistribution.dto.useractivity.UserInactivityPeriodDTO;
import com.app.datadistribution.dto.useractivity.UserSessionHistoryDTO;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.entity.UserLoginSession;
import com.app.datadistribution.enums.LogoutReason;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.security.UserDetailsImpl;
import com.app.datadistribution.service.interfaces.IUserActivityService;

@SpringBootTest(classes = com.app.datadistribution.Application.class)
@Transactional
public class UserActivityIntegrationTest {

    @Autowired
    private IUserActivityService userActivityService;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findAll().stream().findFirst().orElse(null);
        if (testUser != null) {
            UserDetailsImpl userDetails = UserDetailsImpl.build(testUser);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, List.of(
                            new SimpleGrantedAuthority("USER_ACTIVITY_VIEW"),
                            new SimpleGrantedAuthority("USER_ACTIVITY_DETAILS"),
                            new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
    }

    @Test
    @DisplayName("Integration: Full login -> heartbeat -> logout -> getDailyActivity cycle")
    void testFullActivityLifecycle() throws Exception {
        if (testUser == null) return;

        // 1. Record Login
        UserLoginSession session = userActivityService.recordLogin(testUser, "127.0.0.1", "Test-Agent", 1L);
        assertNotNull(session);
        assertNotNull(session.getId());

        // 2. Record Heartbeat
        userActivityService.recordHeartbeat(testUser.getId());

        // 3. Query Daily Activity while WORKING
        UserDailyActivityResponseDTO activityBeforeLogout = userActivityService.getDailyActivity(testUser.getId(), LocalDate.now());
        assertNotNull(activityBeforeLogout);
        assertEquals("WORKING", activityBeforeLogout.getCurrentStatus());
        assertEquals(testUser.getUsername(), activityBeforeLogout.getUsername());

        // 4. Record Logout
        userActivityService.recordLogout(testUser.getId(), "dummy-refresh-token", LogoutReason.MANUAL_LOGOUT);

        // 5. Query Daily Activity after Logout
        UserDailyActivityResponseDTO activityAfterLogout = userActivityService.getDailyActivity(testUser.getId(), LocalDate.now());
        assertNotNull(activityAfterLogout);
        assertEquals("LOGGED_OUT", activityAfterLogout.getCurrentStatus());
        assertNotNull(activityAfterLogout.getFirstLoginAt());
        assertNotNull(activityAfterLogout.getLastLogoutAt());

        // 6. Query Session History
        List<UserSessionHistoryDTO> sessions = userActivityService.getSessionHistory(testUser.getId(), LocalDate.now());
        assertNotNull(sessions);

        // 7. Query Inactivity History
        List<UserInactivityPeriodDTO> inactivities = userActivityService.getInactivityHistory(testUser.getId(), LocalDate.now());
        assertNotNull(inactivities);
    }
}
