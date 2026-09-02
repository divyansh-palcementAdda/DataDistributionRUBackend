package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.dashboard.LowDataUserPageResponseDTO;
import com.app.datadistribution.dto.dashboard.LowDataUserSummaryDTO;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.impl.DashboardServiceImpl;
import java.util.*;
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
class LowDataUsersTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private com.app.datadistribution.service.interfaces.IUserDataScopeService dataScopeService;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private User adminUser;
    private User userZero;
    private User userNine;
    private User userTen;
    private User userInactive;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(dashboardService, "lowDataUserThreshold", 10);

        Role adminRole = Role.builder().name(RoleType.SUPER_ADMIN.name()).build();
        adminUser = User.builder().username("admin").active(true).roles(Set.of(adminRole)).build();
        adminUser.setId(UUID.randomUUID());

        Role counselorRole = Role.builder().name(RoleType.COUNSELOR.name()).build();

        userZero = User.builder().username("user0").firstName("User").lastName("Zero").email("u0@test.com").active(true).roles(Set.of(counselorRole))
                .build();
        userZero.setId(UUID.randomUUID());

        userNine = User.builder().username("user9").firstName("User").lastName("Nine").email("u9@test.com").active(true).roles(Set.of(counselorRole))
                .build();
        userNine.setId(UUID.randomUUID());

        userTen = User.builder().username("user10").firstName("User").lastName("Ten").email("u10@test.com").active(true).roles(Set.of(counselorRole))
                .build();
        userTen.setId(UUID.randomUUID());

        userInactive = User.builder().username("userInactive").active(false).roles(Set.of(counselorRole)).build();
        userInactive.setId(UUID.randomUUID());

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
    void testCountLowDataUsers_ClassifiesUsersBelowThreshold() throws UnauthorizedException, BadRequestException {
        lenient().when(userRepository.findAll()).thenReturn(List.of(userZero, userNine, userTen, userInactive));

        // userZero: 0 unavailed => low data (< 10)
        // userNine: 9 unavailed => low data (< 10)
        // userTen: 10 unavailed => NOT low data (>= 10)
        List<Object[]> unavailedGrouped = List.of(
                new Object[] { userNine.getId(), 9L },
                new Object[] { userTen.getId(), 10L });
        lenient().when(leadRepository.findUnavailedLeadCountsGroupedByUser()).thenReturn(unavailedGrouped);
        lenient().when(leadRepository.findAllottedLeadCountsGroupedByUser()).thenReturn(List.of(
                new Object[] { userNine.getId(), 20L },
                new Object[] { userTen.getId(), 30L }));
        long lowDataCount = dashboardService.countLowDataUsers();
        assertEquals(2, lowDataCount); // userZero (0 < 10) and userNine (9 < 10)
    }

    @Test
    void testGetLowDataUsers_PaginatedResponseAndSorting() throws UnauthorizedException, BadRequestException {
        lenient().when(userRepository.findAll()).thenReturn(List.of(userZero, userNine, userTen));

        List<Object[]> unavailedGrouped = List.of(
                new Object[] { userNine.getId(), 9L },
                new Object[] { userTen.getId(), 10L });
        lenient().when(leadRepository.findUnavailedLeadCountsGroupedByUser()).thenReturn(unavailedGrouped);
        lenient().when(leadRepository.findAllottedLeadCountsGroupedByUser()).thenReturn(List.of());

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).sortBy("remainingDataCount")
                .sortDirection("ASC").build();
        LowDataUserPageResponseDTO response = dashboardService.getLowDataUsers(pageRequest);

        assertNotNull(response);
        assertEquals(2, response.getTotalElements());
        assertEquals(2, response.getContent().size());

        // First user should be userZero (0 remaining data), second should be userNine
        // (9 remaining data)
        assertEquals(userZero.getId(), response.getContent().get(0).getUserId());
        assertEquals(0, response.getContent().get(0).getRemainingDataCount());

        assertEquals(userNine.getId(), response.getContent().get(1).getUserId());
        assertEquals(9, response.getContent().get(1).getRemainingDataCount());
    }

    @Test
    void testGetLowDataUsers_ExcludesAdminAndSuperAdmin() throws UnauthorizedException, BadRequestException {
        Role superAdminRole = Role.builder().name(RoleType.SUPER_ADMIN.name()).build();
        User superAdmin = User.builder().username("superadmin").firstName("Super").lastName("Admin").email("superadmin@test.com").active(true).roles(Set.of(superAdminRole)).build();
        superAdmin.setId(UUID.randomUUID());

        Role adminRole = Role.builder().name(RoleType.ADMIN.name()).build();
        User stdAdmin = User.builder().username("adminUser").firstName("Std").lastName("Admin").email("admin@test.com").active(true).roles(Set.of(adminRole)).build();
        stdAdmin.setId(UUID.randomUUID());

        lenient().when(userRepository.findAll()).thenReturn(List.of(superAdmin, stdAdmin, userZero));
        lenient().when(leadRepository.findUnavailedLeadCountsGroupedByUser()).thenReturn(List.of(
                new Object[]{superAdmin.getId(), 0L},
                new Object[]{stdAdmin.getId(), 0L},
                new Object[]{userZero.getId(), 0L}
        ));
        lenient().when(leadRepository.findAllottedLeadCountsGroupedByUser()).thenReturn(List.of());

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        LowDataUserPageResponseDTO response = dashboardService.getLowDataUsers(pageRequest);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(userZero.getId(), response.getContent().get(0).getUserId());
        assertFalse(response.getContent().stream().anyMatch(u -> u.getUserId().equals(superAdmin.getId())));
        assertFalse(response.getContent().stream().anyMatch(u -> u.getUserId().equals(stdAdmin.getId())));
    }
}
