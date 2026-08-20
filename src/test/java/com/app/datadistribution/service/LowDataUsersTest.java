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

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private User adminUser;
    private User userZero;
    private User userNine;
    private User userTen;
    private User userInactive;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dashboardService, "lowDataUserThreshold", 10);

        Role adminRole = Role.builder().name(RoleType.SUPER_ADMIN.name()).build();
        adminUser = User.builder().username("admin").active(true).roles(Set.of(adminRole)).build();
        adminUser.setId(UUID.randomUUID());

        userZero = User.builder().username("user0").firstName("User").lastName("Zero").email("u0@test.com").active(true)
                .build();
        userZero.setId(UUID.randomUUID());

        userNine = User.builder().username("user9").firstName("User").lastName("Nine").email("u9@test.com").active(true)
                .build();
        userNine.setId(UUID.randomUUID());

        userTen = User.builder().username("user10").firstName("User").lastName("Ten").email("u10@test.com").active(true)
                .build();
        userTen.setId(UUID.randomUUID());

        userInactive = User.builder().username("userInactive").active(false).build();
        userInactive.setId(UUID.randomUUID());

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
}
