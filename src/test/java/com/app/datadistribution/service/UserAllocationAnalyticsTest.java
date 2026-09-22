package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.datadistribution.common.PageResponseDTO;
import com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest;
import com.app.datadistribution.dto.segregation.UserAllocationRowDTO;
import com.app.datadistribution.dto.segregation.UserAllocationSummaryDTO;
import com.app.datadistribution.dto.segregation.UserAllocationUsersResponseDTO;
import com.app.datadistribution.repository.DataSegregationRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.DataSegregationServiceImpl;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

@ExtendWith(MockitoExtension.class)
public class UserAllocationAnalyticsTest {

    @Mock
    private DataSegregationRepository segregationRepository;

    @Mock
    private IUserDataScopeService dataScopeService;

    @InjectMocks
    private DataSegregationServiceImpl segregationService;

    private UserDataScope systemScope;
    private UUID courseId;
    private UUID courseTypeId;

    @BeforeEach
    void setUp() {
        systemScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(UUID.randomUUID())
                .isAdmin(true)
                .build();
        courseId = UUID.randomUUID();
        courseTypeId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getUserAllocationSummary returns total users with allotted data and users currently working")
    void testGetUserAllocationSummary_Success() throws Exception {
        DashboardAnalyticsFilterRequest filter = DashboardAnalyticsFilterRequest.builder()
                .courseIds(List.of(courseId))
                .courseTypeIds(List.of(courseTypeId))
                .build();

        when(dataScopeService.getScopeForCurrentUser(filter)).thenReturn(systemScope);

        UserAllocationSummaryDTO expectedDto = UserAllocationSummaryDTO.builder()
                .totalUsersWithAllottedData(3L)
                .usersCurrentlyWorking(2L)
                .build();

        when(segregationRepository.fetchUserAllocationSummary(eq(filter), eq(systemScope))).thenReturn(expectedDto);

        UserAllocationSummaryDTO result = segregationService.getUserAllocationSummary(filter);

        assertNotNull(result);
        assertEquals(3L, result.getTotalUsersWithAllottedData());
        assertEquals(2L, result.getUsersCurrentlyWorking());
    }

    @Test
    @DisplayName("getUserAllocationUsers returns paginated user list with allotted and working data")
    void testGetUserAllocationUsers_Success() throws Exception {
        DashboardAnalyticsFilterRequest filter = DashboardAnalyticsFilterRequest.builder()
                .courseIds(List.of(courseId))
                .currentlyWorking(true)
                .page(0)
                .size(10)
                .build();

        when(dataScopeService.getScopeForCurrentUser(filter)).thenReturn(systemScope);

        UserAllocationRowDTO userRow = UserAllocationRowDTO.builder()
                .userId(UUID.randomUUID())
                .name("Alice Counselor")
                .username("alice")
                .email("alice@test.com")
                .department("Management")
                .roles(List.of("COUNSELOR"))
                .totalAllottedData(25L)
                .currentlyWorkingData(10L)
                .currentlyWorking(true)
                .lastActivityAt(LocalDateTime.now())
                .build();

        PageResponseDTO<UserAllocationRowDTO> pageResponse = PageResponseDTO.<UserAllocationRowDTO>builder()
                .content(List.of(userRow))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();

        UserAllocationUsersResponseDTO expectedResponse = UserAllocationUsersResponseDTO.builder()
                .totalUsers(1L)
                .currentlyWorkingUsers(1L)
                .totalAllottedData(25L)
                .users(pageResponse)
                .build();

        when(segregationRepository.fetchUserAllocationUsers(eq(filter), eq(systemScope))).thenReturn(expectedResponse);

        UserAllocationUsersResponseDTO result = segregationService.getUserAllocationUsers(filter);

        assertNotNull(result);
        assertEquals(1L, result.getTotalUsers());
        assertEquals(1L, result.getCurrentlyWorkingUsers());
        assertEquals(25L, result.getTotalAllottedData());
        assertEquals(1, result.getUsers().getContent().size());
        assertEquals("Alice Counselor", result.getUsers().getContent().get(0).getName());
    }

    @Test
    @DisplayName("getUserAllocationSummary with null filter creates default request and fetches from repository")
    void testGetUserAllocationSummary_NullFilter() throws Exception {
        when(dataScopeService.getScopeForCurrentUser(any(DashboardAnalyticsFilterRequest.class))).thenReturn(systemScope);

        UserAllocationSummaryDTO expectedDto = UserAllocationSummaryDTO.builder()
                .totalUsersWithAllottedData(10L)
                .usersCurrentlyWorking(4L)
                .build();

        when(segregationRepository.fetchUserAllocationSummary(any(DashboardAnalyticsFilterRequest.class), eq(systemScope)))
                .thenReturn(expectedDto);

        UserAllocationSummaryDTO result = segregationService.getUserAllocationSummary(null);

        assertNotNull(result);
        assertEquals(10L, result.getTotalUsersWithAllottedData());
        assertEquals(4L, result.getUsersCurrentlyWorking());
    }
}
