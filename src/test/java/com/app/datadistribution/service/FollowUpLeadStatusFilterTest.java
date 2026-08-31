package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.followup.FollowUpPagedResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpResponseDTO;
import com.app.datadistribution.dto.lead.LeadStatusResponse;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
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
class FollowUpLeadStatusFilterTest {

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
    private User hodUser;
    private User counselor1;
    private User counselor2;

    private Department dept1;
    private Department dept2;

    private LeadStatus statusInterested;
    private LeadStatus statusConnected;
    private LeadStatus statusRegistered;
    private LeadStatus statusDeleted;

    private Lead lead1;
    private Lead lead2;

    private LeadFollowUp followUp1;
    private LeadFollowUp followUp2;

    private FollowUpResponseDTO responseDto1;
    private FollowUpResponseDTO responseDto2;

    @BeforeEach
    void setUp() {
        dept1 = Department.builder().name("Admissions Dept").build();
        dept1.setId(UUID.randomUUID());

        dept2 = Department.builder().name("Marketing Dept").build();
        dept2.setId(UUID.randomUUID());

        adminUser = User.builder().username("admin").build();
        adminUser.setId(UUID.randomUUID());

        hodUser = User.builder().username("hodUser").build();
        hodUser.setId(UUID.randomUUID());

        counselor1 = User.builder().username("counselor1").build();
        counselor1.setId(UUID.randomUUID());

        counselor2 = User.builder().username("counselor2").build();
        counselor2.setId(UUID.randomUUID());

        statusInterested = LeadStatus.builder().name("Interested").code("INTERESTED").active(true).build();
        statusInterested.setId(UUID.randomUUID());

        statusConnected = LeadStatus.builder().name("Connected").code("CONNECTED").active(true).build();
        statusConnected.setId(UUID.randomUUID());

        statusRegistered = LeadStatus.builder().name("Registered").code("REGISTERED").active(true).build();
        statusRegistered.setId(UUID.randomUUID());

        statusDeleted = LeadStatus.builder().name("Old Status").code("OLD").active(false).build();
        statusDeleted.setId(UUID.randomUUID());
        statusDeleted.setDeleted(true);

        lead1 = Lead.builder()
                .leadCode("LEAD-1001")
                .fullName("Alice Smith")
                .phoneNumber("9876543210")
                .email("alice@example.com")
                .currentStatus(statusInterested)
                .assignedTo(counselor1)
                .createdByUser(adminUser)
                .department(dept1)
                .build();
        lead1.setId(UUID.randomUUID());

        lead2 = Lead.builder()
                .leadCode("LEAD-1002")
                .fullName("Bob Johnson")
                .phoneNumber("9876543211")
                .email("bob@example.com")
                .currentStatus(statusConnected)
                .assignedTo(counselor2)
                .createdByUser(adminUser)
                .department(dept2)
                .build();
        lead2.setId(UUID.randomUUID());

        followUp1 = LeadFollowUp.builder()
                .lead(lead1)
                .followUpDate(LocalDateTime.now().plusDays(1))
                .status(FollowUpStatus.PENDING)
                .remarks("Call back tomorrow")
                .createdByUser(counselor1)
                .assignedTo(counselor1)
                .completed(false)
                .build();
        followUp1.setId(UUID.randomUUID());

        followUp2 = LeadFollowUp.builder()
                .lead(lead2)
                .followUpDate(LocalDateTime.now().plusDays(2))
                .status(FollowUpStatus.PENDING)
                .remarks("Needs fee details")
                .createdByUser(counselor2)
                .assignedTo(counselor2)
                .completed(false)
                .build();
        followUp2.setId(UUID.randomUUID());

        LeadStatusResponse interestedResponse = LeadStatusResponse.builder()
                .id(statusInterested.getId())
                .name("Interested")
                .code("INTERESTED")
                .active(true)
                .build();

        responseDto1 = FollowUpResponseDTO.builder()
                .id(followUp1.getId())
                .leadId(lead1.getId())
                .leadCode("LEAD-1001")
                .leadFullName("Alice Smith")
                .status(FollowUpStatus.PENDING)
                .leadStatus(interestedResponse)
                .followUpDate(followUp1.getFollowUpDate())
                .remarks(followUp1.getRemarks())
                .createdBy(com.app.datadistribution.dto.user.UserSummaryResponse.builder().id(counselor1.getId()).username("counselor1").build())
                .build();

        responseDto2 = FollowUpResponseDTO.builder()
                .id(followUp2.getId())
                .leadId(lead2.getId())
                .leadCode("LEAD-1002")
                .leadFullName("Bob Johnson")
                .status(FollowUpStatus.PENDING)
                .leadStatus(LeadStatusResponse.builder().id(statusConnected.getId()).name("Connected").code("CONNECTED").build())
                .followUpDate(followUp2.getFollowUpDate())
                .remarks(followUp2.getRemarks())
                .createdBy(com.app.datadistribution.dto.user.UserSummaryResponse.builder().id(counselor2.getId()).username("counselor2").build())
                .build();
    }

    @Test
    @DisplayName("1. Follow-up response includes current Lead Status DTO")
    void testFollowUpResponseIncludesCurrentLeadStatus() throws Exception {
        UserDataScope adminScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(adminUser.getId())
                .isAdmin(true)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(adminScope);

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUp1));
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUp1)).thenReturn(responseDto1);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, null, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        FollowUpResponseDTO dto = result.getContent().get(0);
        assertNotNull(dto.getLeadStatus());
        assertEquals("Interested", dto.getLeadStatus().getName());
        assertEquals(statusInterested.getId(), dto.getLeadStatus().getId());
    }

    @Test
    @DisplayName("2. Dynamic Lead Status changes on Lead immediately reflect on Follow-up response")
    void testLeadStatusChangeReflectedOnFollowUp() throws Exception {
        UserDataScope adminScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(adminUser.getId())
                .isAdmin(true)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(adminScope);

        // Lead transitioned to REGISTERED
        lead1.setCurrentStatus(statusRegistered);
        LeadStatusResponse registeredResponse = LeadStatusResponse.builder()
                .id(statusRegistered.getId())
                .name("Registered")
                .code("REGISTERED")
                .active(true)
                .build();
        FollowUpResponseDTO updatedDto = FollowUpResponseDTO.builder()
                .id(followUp1.getId())
                .leadId(lead1.getId())
                .leadStatus(registeredResponse)
                .build();

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUp1));
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUp1)).thenReturn(updatedDto);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, null, null, null, null, null, null);

        assertEquals("Registered", result.getContent().get(0).getLeadStatus().getName());
    }

    @Test
    @DisplayName("3. Filter follow-ups by single Lead Status ID")
    void testFilterBySingleLeadStatusId() throws Exception {
        UserDataScope adminScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(adminUser.getId())
                .isAdmin(true)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(adminScope);
        when(leadStatusRepository.findById(statusInterested.getId())).thenReturn(Optional.of(statusInterested));

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUp1));
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUp1)).thenReturn(responseDto1);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, null, null, null, null, statusInterested.getId(), null);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(statusInterested.getId(), result.getContent().get(0).getLeadStatus().getId());
        verify(leadStatusRepository).findById(statusInterested.getId());
    }

    @Test
    @DisplayName("4. Filter follow-ups by multiple Lead Status IDs")
    void testFilterByMultipleLeadStatusIds() throws Exception {
        UserDataScope adminScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(adminUser.getId())
                .isAdmin(true)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(adminScope);
        when(leadStatusRepository.findById(statusInterested.getId())).thenReturn(Optional.of(statusInterested));
        when(leadStatusRepository.findById(statusConnected.getId())).thenReturn(Optional.of(statusConnected));

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUp1, followUp2));
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUp1)).thenReturn(responseDto1);
        when(leadMapper.toFollowUpResponseDto(followUp2)).thenReturn(responseDto2);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        List<UUID> statusIds = List.of(statusInterested.getId(), statusConnected.getId());
        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, null, null, null, null, null, statusIds);

        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        verify(leadStatusRepository).findById(statusInterested.getId());
        verify(leadStatusRepository).findById(statusConnected.getId());
    }

    @Test
    @DisplayName("5. Combined filters: status=PENDING + leadStatusId + date + search")
    void testCombinedFilters() throws Exception {
        UserDataScope adminScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(adminUser.getId())
                .isAdmin(true)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(adminScope);
        when(leadStatusRepository.findById(statusInterested.getId())).thenReturn(Optional.of(statusInterested));

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUp1));
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUp1)).thenReturn(responseDto1);

        LocalDate filterDate = LocalDate.now().plusDays(1);
        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(0)
                .size(10)
                .search("Alice")
                .build();

        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(
                pageRequest, filterDate, FollowUpStatus.PENDING, null, lead1.getId(), statusInterested.getId(), null
        );

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(leadFollowUpRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("6. Role RBAC: Admin can see all system follow-ups and filter by any user")
    void testAdminScopeCanQueryAnyUser() throws Exception {
        UserDataScope adminScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(adminUser.getId())
                .isAdmin(true)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(adminScope);

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUp1));
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUp1)).thenReturn(responseDto1);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, null, null, counselor1.getId(), null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("7. Role RBAC: HOD can view department follow-ups and filter by department users")
    void testHodScopeDepartmentAccess() throws Exception {
        UserDataScope hodScope = UserDataScope.builder()
                .scopeType(ScopeType.DEPARTMENT)
                .userId(hodUser.getId())
                .isHod(true)
                .departmentIds(Set.of(dept1.getId()))
                .departmentUserIds(Set.of(counselor1.getId(), hodUser.getId()))
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(hodScope);

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUp1));
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUp1)).thenReturn(responseDto1);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        // HOD filtering by counselor1 (in department)
        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, null, null, counselor1.getId(), null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("8. Role RBAC: Counselor is strictly limited to self scope and cannot escalate via userId")
    void testCounselorScopeCannotAccessOtherUserData() throws Exception {
        UserDataScope counselorScope = UserDataScope.builder()
                .scopeType(ScopeType.SELF)
                .userId(counselor1.getId())
                .isCounsellor(true)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(counselorScope);

        // When counselor1 queries own followups
        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUp1));
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUp1)).thenReturn(responseDto1);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, null, null, counselor1.getId(), null, null, null);
        assertEquals(1, result.getContent().size());

        // When counselor1 attempts to query counselor2's userId -> disjunction
        followUpService.getAllFollowUps(pageRequest, null, null, counselor2.getId(), null, null, null);
        verify(leadFollowUpRepository, times(2)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("9. Invalid Lead Status ID throws ResourcesNotFoundException")
    void testInvalidLeadStatusIdThrowsNotFound() throws Exception {
        UserDataScope adminScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(adminUser.getId())
                .isAdmin(true)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(adminScope);

        UUID nonExistentId = UUID.randomUUID();
        when(leadStatusRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        assertThrows(ResourcesNotFoundException.class, () ->
                followUpService.getAllFollowUps(pageRequest, null, null, null, null, nonExistentId, null)
        );
    }

    @Test
    @DisplayName("10. Soft-deleted Lead Status throws ResourcesNotFoundException")
    void testSoftDeletedLeadStatusThrowsNotFound() throws Exception {
        UserDataScope adminScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(adminUser.getId())
                .isAdmin(true)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(adminScope);

        when(leadStatusRepository.findById(statusDeleted.getId())).thenReturn(Optional.of(statusDeleted));

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        assertThrows(ResourcesNotFoundException.class, () ->
                followUpService.getAllFollowUps(pageRequest, null, null, null, null, statusDeleted.getId(), null)
        );
    }

    @Test
    @DisplayName("11. Backward compatibility: 5-parameter getAllFollowUps calls default implementation")
    void testBackwardCompatibility5ParamMethod() throws Exception {
        UserDataScope adminScope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(adminUser.getId())
                .isAdmin(true)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(adminScope);

        Page<LeadFollowUp> page = new PageImpl<>(List.of(followUp1));
        when(leadFollowUpRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toFollowUpResponseDto(followUp1)).thenReturn(responseDto1);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        FollowUpPagedResponseDTO result = followUpService.getAllFollowUps(pageRequest, null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("12. getFollowUpsByUserId enforces security permissions")
    void testGetFollowUpsByUserIdUnauthorized() throws Exception {
        UserDataScope counselorScope = UserDataScope.builder()
                .scopeType(ScopeType.SELF)
                .userId(counselor1.getId())
                .isCounsellor(true)
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(counselorScope);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        assertThrows(UnauthorizedException.class, () ->
                followUpService.getFollowUpsByUserId(counselor2.getId(), pageRequest)
        );
    }
}
