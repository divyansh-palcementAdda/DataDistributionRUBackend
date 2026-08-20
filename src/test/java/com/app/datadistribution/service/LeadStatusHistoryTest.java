package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.lead.LeadStatusHistoryPageResponse;
import com.app.datadistribution.dto.lead.LeadStatusHistoryResponse;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.*;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.LeadServiceImpl;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private CourseRepository courseRepository;
    @Mock
    private IUserDataScopeService dataScopeService;
    @Mock
    private LeadMapper leadMapper;

    @InjectMocks
    private LeadServiceImpl leadService;

    private UUID leadId;
    private Lead lead;
    private LeadStatus statusRaw;
    private LeadStatus statusConnected;
    private User currentUser;

    @BeforeEach
    void setUp() {
        leadId = UUID.randomUUID();
        UUID status1Id = UUID.randomUUID();
        UUID status2Id = UUID.randomUUID();

        statusRaw = LeadStatus.builder().name("Raw").code("RAW").build();
        statusRaw.setId(status1Id);

        statusConnected = LeadStatus.builder().name("Connected").code("CONNECTED").build();
        statusConnected.setId(status2Id);

        currentUser = User.builder().username("admin").build();
        currentUser.setId(UUID.randomUUID());

        lead = Lead.builder()
                .leadCode("LEAD-1001")
                .fullName("John Doe")
                .phoneNumber("9876543210")
                .currentStatus(statusRaw)
                .build();
        lead.setId(leadId);
    }

    @Test
    void testUpdate_StatusChange_CreatesHistoryRecord() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("admin");
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        lenient().when(userRepository.findByUsername("admin")).thenReturn(Optional.of(currentUser));
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(leadStatusRepository.findById(statusConnected.getId())).thenReturn(Optional.of(statusConnected));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        LeadRequest request = LeadRequest.builder()
                .fullName("John Doe")
                .phoneNumber("9876543210")
                .statusId(statusConnected.getId())
                .remarks("Interested in CS")
                .build();

        leadService.update(leadId, request);

        verify(leadStatusHistoryRepository, times(1)).save(any(LeadStatusHistory.class));
    }

    @Test
    void testUpdate_SameStatus_DoesNotCreateDuplicateHistoryRecord() {
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(leadStatusRepository.findById(statusRaw.getId())).thenReturn(Optional.of(statusRaw));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        LeadRequest request = LeadRequest.builder()
                .fullName("John Doe Updated")
                .phoneNumber("9876543210")
                .statusId(statusRaw.getId())
                .build();

        leadService.update(leadId, request);

        verify(leadStatusHistoryRepository, never()).save(any(LeadStatusHistory.class));
    }

    @Test
    void testGetStatusHistoryByLeadId_Paginated_Success() throws UnauthorizedException {
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));

        UserDataScope scope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .userId(currentUser.getId())
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(scope);

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
        when(leadMapper.toDto(any(LeadStatusHistory.class))).thenReturn(LeadStatusHistoryResponse.builder().id(history.getId()).build());

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        LeadStatusHistoryPageResponse response = leadService.getStatusHistoryByLeadId(leadId, pageRequest);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(1, response.getTotalElements());
    }
}
