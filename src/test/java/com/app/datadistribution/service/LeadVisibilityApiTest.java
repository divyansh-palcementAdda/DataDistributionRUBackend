package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadPageResponse;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.lead.LeadStatusChangeRequest;
import com.app.datadistribution.entity.*;
import com.app.datadistribution.enums.HodAccessType;
import com.app.datadistribution.enums.RoleType;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class LeadVisibilityApiTest {

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
    private ILeadDataScopeService leadDataScopeService;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private LeadServiceImpl leadService;

    private User adminUser;
    private User hodUser;
    private User counselor1;
    private User counselor2;

    private Department dept1;
    private Department dept2;

    private Lead lead1;
    private Lead lead2;

    @BeforeEach
    void setUp() {
        dept1 = Department.builder().name("Dept 1").build();
        dept1.setId(UUID.randomUUID());

        dept2 = Department.builder().name("Dept 2").build();
        dept2.setId(UUID.randomUUID());

        adminUser = User.builder().username("admin").build();
        adminUser.setId(UUID.randomUUID());

        hodUser = User.builder().username("hod").build();
        hodUser.setId(UUID.randomUUID());

        counselor1 = User.builder().username("counselor1").build();
        counselor1.setId(UUID.randomUUID());

        counselor2 = User.builder().username("counselor2").build();
        counselor2.setId(UUID.randomUUID());

        lead1 = Lead.builder()
                .leadCode("LEAD-101")
                .fullName("John Doe")
                .department(dept1)
                .assignedTo(counselor1)
                .createdByUser(counselor1)
                .build();
        lead1.setId(UUID.randomUUID());

        lead2 = Lead.builder()
                .leadCode("LEAD-102")
                .fullName("Jane Smith")
                .department(dept2)
                .assignedTo(counselor2)
                .createdByUser(counselor2)
                .build();
        lead2.setId(UUID.randomUUID());
    }

    private void mockSecurityContext(User user) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(user.getUsername());
        when(auth.getPrincipal()).thenReturn(user.getUsername());

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    @Test
    void testGetById_Authorized() throws Exception {
        UUID leadId = lead1.getId();
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead1));

        UserDataScope scope = UserDataScope.builder()
                .userId(counselor1.getId())
                .scopeType(ScopeType.SELF)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        doNothing().when(leadDataScopeService).validateLeadReadAccess(lead1, scope);

        LeadResponse expectedDto = LeadResponse.builder().id(leadId).leadCode("LEAD-101").build();
        when(leadMapper.toDto(lead1)).thenReturn(expectedDto);

        LeadResponse result = leadService.getById(leadId);
        assertNotNull(result);
        assertEquals("LEAD-101", result.getLeadCode());
        verify(leadDataScopeService).validateLeadReadAccess(lead1, scope);
    }

    @Test
    void testGetById_Unauthorized_ThrowsException() throws Exception {
        UUID leadId = lead2.getId();
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead2));

        UserDataScope scope = UserDataScope.builder()
                .userId(counselor1.getId())
                .scopeType(ScopeType.SELF)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        doThrow(new UnauthorizedException("You do not have access to this lead based on your assigned role and data scope."))
                .when(leadDataScopeService).validateLeadReadAccess(lead2, scope);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> leadService.getById(leadId));
        assertTrue(ex.getMessage().contains("assigned role and data scope"));
    }

    @Test
    void testGetAllLeads_AppliesScopeSpecification() throws Exception {
        UserDataScope scope = UserDataScope.builder()
                .userId(counselor1.getId())
                .scopeType(ScopeType.SELF)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);

        Specification<Lead> mockSpec = mock(Specification.class);
        when(leadDataScopeService.getLeadScopeSpecification(scope)).thenReturn(mockSpec);

        Page<Lead> mockPage = new PageImpl<>(List.of(lead1));
        when(leadRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);
        when(leadMapper.toDto(lead1)).thenReturn(LeadResponse.builder().id(lead1.getId()).leadCode("LEAD-101").build());

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();
        LeadPageResponse response = leadService.getAllLeads(
                pageRequest, null, null, null, null, null, null, null, null, null, null, null, null
        );

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());
        assertEquals("LEAD-101", response.getContent().get(0).getLeadCode());
    }

    @Test
    void testCounselor_CannotAssignLeadToAnotherUser() throws Exception {
        mockSecurityContext(counselor1);

        LeadRequest request = LeadRequest.builder()
                .fullName("New Student")
                .phoneNumber("9876543210")
                .assignedToUserId(counselor2.getId()) // Counselor 1 trying to assign to Counselor 2
                .build();

        UserDataScope scope = UserDataScope.builder()
                .userId(counselor1.getId())
                .scopeType(ScopeType.SELF)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        when(userRepository.findById(counselor2.getId())).thenReturn(Optional.of(counselor2));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> leadService.create(request));
        assertTrue(ex.getMessage().contains("Counselors can only assign leads to themselves"));
    }

    @Test
    void testHod_CannotAssignToDepartmentOutsideScope() throws Exception {
        mockSecurityContext(hodUser);

        LeadRequest request = LeadRequest.builder()
                .fullName("New Student")
                .phoneNumber("9876543210")
                .departmentId(dept2.getId()) // HOD mapped to dept1 only
                .build();

        UserDataScope scope = UserDataScope.builder()
                .userId(hodUser.getId())
                .scopeType(ScopeType.DEPARTMENT)
                .isHod(true)
                .departmentIds(Set.of(dept1.getId()))
                .departmentUserIds(Set.of(counselor1.getId(), hodUser.getId()))
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        when(departmentRepository.findById(dept2.getId())).thenReturn(Optional.of(dept2));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> leadService.create(request));
        assertTrue(ex.getMessage().contains("HOD can only create leads within their assigned department"));
    }

    @Test
    void testUpdate_WriteAccessValidated() throws Exception {
        UUID leadId = lead1.getId();
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead1));

        UserDataScope scope = UserDataScope.builder()
                .userId(hodUser.getId())
                .scopeType(ScopeType.DEPARTMENT)
                .isHod(true)
                .departmentIds(Set.of(dept1.getId()))
                .departmentUserIds(Set.of(counselor1.getId(), hodUser.getId()))
                .hodAccessType(HodAccessType.VIEW_ONLY)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        doThrow(new UnauthorizedException("HOD with VIEW_ONLY access cannot modify department leads."))
                .when(leadDataScopeService).validateLeadWriteAccess(lead1, scope);

        LeadRequest request = LeadRequest.builder()
                .fullName("Updated Name")
                .phoneNumber("9876543210")
                .build();

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> leadService.update(leadId, request));
        assertTrue(ex.getMessage().contains("VIEW_ONLY"));
    }

    @Test
    void testDeleteLead_WriteAccessValidated() throws Exception {
        UUID leadId = lead2.getId();
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead2));

        UserDataScope scope = UserDataScope.builder()
                .userId(counselor1.getId())
                .scopeType(ScopeType.SELF)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        doThrow(new UnauthorizedException("You do not have access to this lead based on your assigned role and data scope."))
                .when(leadDataScopeService).validateLeadWriteAccess(lead2, scope);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> leadService.deleteLead(leadId));
        assertTrue(ex.getMessage().contains("assigned role and data scope"));
    }
}
