package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.lead.LeadStatusChangeRequest;
import com.app.datadistribution.dto.lead.LeadStatusHistoryPageResponse;
import com.app.datadistribution.dto.lead.LeadStatusHistoryResponse;
import com.app.datadistribution.dto.lead.LeadStatusResponse;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFeedback;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.*;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.LeadServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class LeadStatusHistoryTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadSourceRepository leadSourceRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LeadStatusHistoryRepository leadStatusHistoryRepository;
    @Mock
    private LeadFeedbackRepository leadFeedbackRepository;
    @Mock
    private LeadAvailedRepository leadAvailedRepository;
    @Mock
    private LeadAssignmentHistoryRepository leadAssignmentHistoryRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private IUserDataScopeService dataScopeService;
    @Mock
    private ILeadDataScopeService leadDataScopeService;
    @Mock
    private LeadMapper leadMapper;

    @InjectMocks
    private LeadServiceImpl leadService;

    private UUID leadId;
    private Lead lead;
    private LeadStatus statusRaw;
    private LeadStatus statusConnected;
    private LeadStatus statusInterested;
    private LeadStatus statusRegistered;
    private LeadStatus statusInactive;
    private User currentUser;
    private User counselorA;
    private User counselorB;

    @BeforeEach
    void setUp() {
        leadId = UUID.randomUUID();
        UUID status1Id = UUID.randomUUID();
        UUID status2Id = UUID.randomUUID();
        UUID status3Id = UUID.randomUUID();
        UUID status4Id = UUID.randomUUID();
        UUID status5Id = UUID.randomUUID();

        statusRaw = LeadStatus.builder().name("Raw").code("RAW").active(true).build();
        statusRaw.setId(status1Id);

        statusConnected = LeadStatus.builder().name("Connected").code("CONNECTED").active(true).build();
        statusConnected.setId(status2Id);

        statusInterested = LeadStatus.builder().name("Interested").code("INTERESTED").active(true).build();
        statusInterested.setId(status3Id);

        statusRegistered = LeadStatus.builder().name("Registered").code("REGISTERED").active(true).build();
        statusRegistered.setId(status4Id);

        statusInactive = LeadStatus.builder().name("Archived Status").code("ARCHIVED").active(false).build();
        statusInactive.setId(status5Id);

        currentUser = User.builder().username("admin").build();
        currentUser.setId(UUID.randomUUID());

        counselorA = User.builder().username("counselorA").build();
        counselorA.setId(UUID.randomUUID());

        counselorB = User.builder().username("counselorB").build();
        counselorB.setId(UUID.randomUUID());

        lead = Lead.builder()
                .leadCode("LEAD-1001")
                .fullName("John Doe")
                .phoneNumber("9876543210")
                .currentStatus(statusRaw)
                .assignedTo(currentUser)
                .build();
        lead.setId(leadId);
    }

    private void mockSecurityContext(User user) {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn(user.getUsername());
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    @Test
    void testCreate_CreatesInitialStatusHistoryRecord() throws Exception {
        mockSecurityContext(currentUser);

        UserDataScope scope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(currentUser.getId())
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);

        when(leadStatusRepository.findById(statusRaw.getId())).thenReturn(Optional.of(statusRaw));
        when(leadMapper.toEntity(any(LeadRequest.class))).thenReturn(new Lead());
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> {
            Lead l = inv.getArgument(0);
            l.setId(leadId);
            return l;
        });
        when(leadStatusHistoryRepository.save(any(LeadStatusHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadMapper.toDto(any(Lead.class))).thenReturn(LeadResponse.builder().id(leadId).build());

        LeadRequest request = LeadRequest.builder()
                .fullName("Jane Doe")
                .phoneNumber("9123456780")
                .statusId(statusRaw.getId())
                .build();

        leadService.create(request);

        ArgumentCaptor<LeadStatusHistory> historyCaptor = ArgumentCaptor.forClass(LeadStatusHistory.class);
        verify(leadStatusHistoryRepository, times(1)).save(historyCaptor.capture());

        LeadStatusHistory captured = historyCaptor.getValue();
        assertNull(captured.getPreviousStatus(), "Initial history record must have null previousStatus");
        assertEquals(statusRaw, captured.getNewStatus(), "Initial history record must have initial status");
        assertEquals("Lead registered in system.", captured.getFeedback());
    }

    @Test
    void testChangeStatus_SingleTransition_RAW_to_CONNECTED() throws Exception {
        mockSecurityContext(currentUser);

        UserDataScope scope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(currentUser.getId())
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(leadStatusRepository.findById(statusConnected.getId())).thenReturn(Optional.of(statusConnected));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadStatusHistoryRepository.save(any(LeadStatusHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadMapper.toDto(any(Lead.class))).thenReturn(LeadResponse.builder().id(leadId).build());

        LeadStatusChangeRequest request = LeadStatusChangeRequest.builder()
                .newStatusId(statusConnected.getId())
                .feedback("Customer attended the demo call")
                .build();

        leadService.changeStatus(leadId, request);

        assertEquals(statusConnected, lead.getCurrentStatus());

        ArgumentCaptor<LeadStatusHistory> historyCaptor = ArgumentCaptor.forClass(LeadStatusHistory.class);
        verify(leadStatusHistoryRepository, times(1)).save(historyCaptor.capture());

        LeadStatusHistory captured = historyCaptor.getValue();
        assertEquals(statusRaw, captured.getPreviousStatus());
        assertEquals(statusConnected, captured.getNewStatus());
        assertEquals("Customer attended the demo call", captured.getFeedback());
        assertEquals(currentUser, captured.getChangedByUser());

        // Verify feedback record also saved
        ArgumentCaptor<LeadFeedback> feedbackCaptor = ArgumentCaptor.forClass(LeadFeedback.class);
        verify(leadFeedbackRepository, times(1)).save(feedbackCaptor.capture());
        assertEquals(statusConnected, feedbackCaptor.getValue().getStatusAtTime());
        assertEquals("Customer attended the demo call", feedbackCaptor.getValue().getFeedback());
    }

    @Test
    void testChangeStatus_MultipleTransitions_PreservesCompleteTimeline() throws Exception {
        mockSecurityContext(currentUser);

        UserDataScope scope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(currentUser.getId())
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadStatusHistoryRepository.save(any(LeadStatusHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadMapper.toDto(any(Lead.class))).thenReturn(LeadResponse.builder().id(leadId).build());

        // 1. Transition RAW -> CONNECTED
        when(leadStatusRepository.findById(statusConnected.getId())).thenReturn(Optional.of(statusConnected));
        leadService.changeStatus(leadId, LeadStatusChangeRequest.builder()
                .newStatusId(statusConnected.getId())
                .feedback("Transition 1: RAW -> CONNECTED")
                .build());
        assertEquals(statusConnected, lead.getCurrentStatus());

        // 2. Transition CONNECTED -> INTERESTED
        when(leadStatusRepository.findById(statusInterested.getId())).thenReturn(Optional.of(statusInterested));
        leadService.changeStatus(leadId, LeadStatusChangeRequest.builder()
                .newStatusId(statusInterested.getId())
                .feedback("Transition 2: CONNECTED -> INTERESTED")
                .build());
        assertEquals(statusInterested, lead.getCurrentStatus());

        // 3. Transition INTERESTED -> REGISTERED
        when(leadStatusRepository.findById(statusRegistered.getId())).thenReturn(Optional.of(statusRegistered));
        leadService.changeStatus(leadId, LeadStatusChangeRequest.builder()
                .newStatusId(statusRegistered.getId())
                .feedback("Transition 3: INTERESTED -> REGISTERED")
                .build());
        assertEquals(statusRegistered, lead.getCurrentStatus());

        // Verify 3 distinct history records saved
        ArgumentCaptor<LeadStatusHistory> historyCaptor = ArgumentCaptor.forClass(LeadStatusHistory.class);
        verify(leadStatusHistoryRepository, times(3)).save(historyCaptor.capture());

        List<LeadStatusHistory> allSaved = historyCaptor.getAllValues();
        assertEquals(3, allSaved.size());

        // First transition: RAW -> CONNECTED
        assertEquals(statusRaw, allSaved.get(0).getPreviousStatus());
        assertEquals(statusConnected, allSaved.get(0).getNewStatus());

        // Second transition: CONNECTED -> INTERESTED
        assertEquals(statusConnected, allSaved.get(1).getPreviousStatus());
        assertEquals(statusInterested, allSaved.get(1).getNewStatus());

        // Third transition: INTERESTED -> REGISTERED
        assertEquals(statusInterested, allSaved.get(2).getPreviousStatus());
        assertEquals(statusRegistered, allSaved.get(2).getNewStatus());
    }

    @Test
    void testChangeStatus_SameStatus_DoesNotCreateDuplicateHistory() throws Exception {
        mockSecurityContext(currentUser);

        lead.setCurrentStatus(statusConnected);
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(leadStatusRepository.findById(statusConnected.getId())).thenReturn(Optional.of(statusConnected));

        UserDataScope scope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(currentUser.getId())
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        when(leadMapper.toDto(any(Lead.class))).thenReturn(LeadResponse.builder().id(leadId).build());

        LeadStatusChangeRequest request = LeadStatusChangeRequest.builder()
                .newStatusId(statusConnected.getId())
                .feedback("Followup call again, still connected")
                .build();

        leadService.changeStatus(leadId, request);

        // Status history must NOT be saved for same status transition
        verify(leadStatusHistoryRepository, never()).save(any(LeadStatusHistory.class));
        // Feedback should still be saved
        verify(leadFeedbackRepository, times(1)).save(any(LeadFeedback.class));
    }

    @Test
    void testChangeStatus_InactiveStatus_ThrowsBadRequestException() throws Exception {
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(leadStatusRepository.findById(statusInactive.getId())).thenReturn(Optional.of(statusInactive));

        UserDataScope scope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(currentUser.getId())
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);

        LeadStatusChangeRequest request = LeadStatusChangeRequest.builder()
                .newStatusId(statusInactive.getId())
                .feedback("Trying to set inactive status")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> leadService.changeStatus(leadId, request));
        assertTrue(ex.getMessage().contains("inactive"));
        verify(leadStatusHistoryRepository, never()).save(any(LeadStatusHistory.class));
    }

    @Test
    void testChangeStatus_UnauthorizedUser_ThrowsUnauthorizedException() throws Exception {
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));

        UserDataScope counselorBScope = UserDataScope.builder()
                .scopeType(ScopeType.SELF)
                .userId(counselorB.getId())
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(counselorBScope);

        doThrow(new UnauthorizedException("You do not have access to this lead based on your assigned role and data scope."))
                .when(leadDataScopeService).validateLeadWriteAccess(eq(lead), eq(counselorBScope));

        LeadStatusChangeRequest request = LeadStatusChangeRequest.builder()
                .newStatusId(statusConnected.getId())
                .feedback("Attempting unauthorized status change")
                .build();

        assertThrows(UnauthorizedException.class, () -> leadService.changeStatus(leadId, request));
        verify(leadStatusHistoryRepository, never()).save(any(LeadStatusHistory.class));
    }

    @Test
    void testUpdate_StatusChange_UsesCentralizedStatusChangeFlow() throws Exception {
        mockSecurityContext(currentUser);

        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(leadStatusRepository.findById(statusConnected.getId())).thenReturn(Optional.of(statusConnected));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadStatusHistoryRepository.save(any(LeadStatusHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDataScope scope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(currentUser.getId())
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);

        LeadRequest request = LeadRequest.builder()
                .fullName("John Doe")
                .phoneNumber("9876543210")
                .statusId(statusConnected.getId())
                .remarks("Interested in CS")
                .build();

        leadService.update(leadId, request);

        ArgumentCaptor<LeadStatusHistory> historyCaptor = ArgumentCaptor.forClass(LeadStatusHistory.class);
        verify(leadStatusHistoryRepository, times(1)).save(historyCaptor.capture());

        LeadStatusHistory captured = historyCaptor.getValue();
        assertEquals(statusRaw, captured.getPreviousStatus());
        assertEquals(statusConnected, captured.getNewStatus());
        assertEquals("Interested in CS", captured.getFeedback());
    }

    @Test
    void testGetStatusHistoryByLeadId_List_ReturnsDeterministicOrderedHistory() throws Exception {
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));

        UserDataScope scope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(currentUser.getId())
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);

        LeadStatusHistory history1 = LeadStatusHistory.builder()
                .lead(lead)
                .previousStatus(statusRaw)
                .newStatus(statusConnected)
                .changedByUser(currentUser)
                .feedback("RAW -> CONNECTED")
                .build();
        history1.setId(UUID.randomUUID());

        when(leadStatusHistoryRepository.findByLeadIdOrderByCreatedAtDescIdDesc(leadId)).thenReturn(List.of(history1));
        when(leadMapper.toDto(history1)).thenReturn(LeadStatusHistoryResponse.builder()
                .id(history1.getId())
                .previousStatus(LeadStatusResponse.builder().id(statusRaw.getId()).name("Raw").build())
                .newStatus(LeadStatusResponse.builder().id(statusConnected.getId()).name("Connected").build())
                .feedback("RAW -> CONNECTED")
                .build());

        List<LeadStatusHistoryResponse> responses = leadService.getStatusHistoryByLeadId(leadId);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Raw", responses.get(0).getPreviousStatus().getName());
        assertEquals("Raw", responses.get(0).getOldStatus().getName());
        assertEquals("Connected", responses.get(0).getNewStatus().getName());
        assertEquals("Connected", responses.get(0).getCurrentStatus().getName());
        assertEquals("RAW -> CONNECTED", responses.get(0).getFeedback());
        assertEquals("RAW -> CONNECTED", responses.get(0).getRemarks());
    }

    @Test
    void testGetStatusHistoryByLeadId_Paginated_Success() throws UnauthorizedException, BadRequestException {
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));

        UserDataScope scope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(currentUser.getId())
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);

        LeadStatusHistory history = LeadStatusHistory.builder()
                .lead(lead)
                .previousStatus(statusRaw)
                .newStatus(statusConnected)
                .changedByUser(currentUser)
                .feedback("Status updated")
                .build();
        history.setId(UUID.randomUUID());

        Page<LeadStatusHistory> page = new PageImpl<>(List.of(history));
        when(leadStatusHistoryRepository.findByLeadId(eq(leadId), any(Pageable.class))).thenReturn(page);
        when(leadMapper.toDto(any(LeadStatusHistory.class))).thenReturn(LeadStatusHistoryResponse.builder()
                .id(history.getId())
                .previousStatus(LeadStatusResponse.builder().id(statusRaw.getId()).name("Raw").build())
                .newStatus(LeadStatusResponse.builder().id(statusConnected.getId()).name("Connected").build())
                .feedback("Status updated")
                .build());

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).sortBy("changedAt").sortDirection("DESC").build();
        LeadStatusHistoryPageResponse response = leadService.getStatusHistoryByLeadId(leadId, pageRequest);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(1, response.getTotalElements());
        assertEquals("Raw", response.getContent().get(0).getOldStatus().getName());
        assertEquals("Connected", response.getContent().get(0).getCurrentStatus().getName());
    }
}
