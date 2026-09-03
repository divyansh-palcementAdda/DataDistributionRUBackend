package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.lead.LeadAssignmentRequest;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.lead.LeadStatusChangeRequest;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.LeadAssignmentHistoryRepository;
import com.app.datadistribution.repository.LeadFeedbackRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusHistoryRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.LeadAssignmentServiceImpl;
import com.app.datadistribution.service.impl.LeadServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.util.LeadDepartmentResolver;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeadDepartmentSyncAndStatusTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private LeadStatusHistoryRepository leadStatusHistoryRepository;
    @Mock
    private LeadFeedbackRepository leadFeedbackRepository;
    @Mock
    private LeadAssignmentHistoryRepository leadAssignmentHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private ILeadDataScopeService leadDataScopeService;
    @Mock
    private com.app.datadistribution.service.interfaces.ILeadStatusTransitionService leadStatusTransitionService;
    @Mock
    private com.app.datadistribution.integration.cms.service.IStudentVerificationService studentVerificationService;

    @InjectMocks
    private LeadServiceImpl leadService;

    @InjectMocks
    private LeadAssignmentServiceImpl leadAssignmentService;

    private Department deptA;
    private Department deptB;
    private User counselorA;
    private User counselorB;
    private User hodUser;
    private User adminUser;
    private LeadStatus statusNew;
    private LeadStatus statusContacted;

    @BeforeEach
    void setUp() throws BadRequestException {
        deptA = Department.builder().name("Department A").code("DEP_A").active(true).build();
        deptA.setId(UUID.randomUUID());

        deptB = Department.builder().name("Department B").code("DEP_B").active(true).build();
        deptB.setId(UUID.randomUUID());

        counselorA = User.builder().username("counselorA").departments(new HashSet<>(Set.of(deptA))).active(true).build();
        counselorA.setId(UUID.randomUUID());

        counselorB = User.builder().username("counselorB").departments(new HashSet<>(Set.of(deptB))).active(true).build();
        counselorB.setId(UUID.randomUUID());

        hodUser = User.builder().username("hodUser").departments(new HashSet<>(Set.of(deptA))).active(true).build();
        hodUser.setId(UUID.randomUUID());

        adminUser = User.builder().username("adminUser").departments(new HashSet<>()).active(true).build();
        adminUser.setId(UUID.randomUUID());

        statusNew = LeadStatus.builder().name("NEW").code("NEW").active(true).build();
        statusNew.setId(UUID.randomUUID());

        statusContacted = LeadStatus.builder().name("CONTACTED").code("CONTACTED").active(true).build();
        statusContacted.setId(UUID.randomUUID());

        when(departmentRepository.findById(deptA.getId())).thenReturn(Optional.of(deptA));
        when(departmentRepository.findById(deptB.getId())).thenReturn(Optional.of(deptB));
        when(userRepository.findById(counselorA.getId())).thenReturn(Optional.of(counselorA));
        when(userRepository.findById(counselorB.getId())).thenReturn(Optional.of(counselorB));
        when(userRepository.findById(hodUser.getId())).thenReturn(Optional.of(hodUser));
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(leadStatusRepository.findByCodeIgnoreCase("RAW")).thenReturn(Optional.of(statusNew));
        when(leadStatusRepository.findById(statusNew.getId())).thenReturn(Optional.of(statusNew));
        when(leadStatusRepository.findById(statusContacted.getId())).thenReturn(Optional.of(statusContacted));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadMapper.toDto(any(Lead.class))).thenReturn(LeadResponse.builder().build());
        lenient().when(studentVerificationService.verifyStudent(any())).thenReturn(
                com.app.datadistribution.integration.cms.dto.StudentVerificationResponse.builder()
                        .verified(true)
                        .matchStatus(com.app.datadistribution.integration.cms.enums.MatchStatus.FULL_MATCH)
                        .confidenceScore(100)
                        .build()
        );
        when(leadStatusTransitionService.executeStatusTransition(any(Lead.class), any(LeadStatus.class), any(User.class), any()))
                .thenAnswer(inv -> {
                    Lead l = inv.getArgument(0);
                    LeadStatus s = inv.getArgument(1);
                    l.setCurrentStatus(s);
                    l.setLastContactedAt(LocalDateTime.now());
                    return l;
                });
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
    @DisplayName("TEST 1: Counselor creates Lead -> assignedUser = Counselor A, department = Department A")
    void test1_CounselorCreatesLead_AutoAssignedAndDepartmentSynchronized() throws Exception {
        mockSecurityContext(counselorA);

        UserDataScope scope = UserDataScope.builder()
                .userId(counselorA.getId())
                .scopeType(ScopeType.SELF)
                .isSelfScopeOnly(true)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);

        LeadRequest request = LeadRequest.builder()
                .fullName("John Doe")
                .phoneNumber("9876543210")
                .build();
        when(leadMapper.toEntity(request)).thenReturn(new Lead());

        leadService.create(request);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        Lead saved = captor.getValue();

        assertEquals(counselorA, saved.getAssignedTo());
        assertNotNull(saved.getDepartment());
        assertEquals(deptA.getId(), saved.getDepartment().getId());
    }

    @Test
    @DisplayName("TEST 2: Counselor attempts conflicting department -> Department overridden to Counselor A's department")
    void test2_CounselorAttemptsConflictingDepartment_OverriddenToAuthoritativeDepartment() throws Exception {
        mockSecurityContext(counselorA);

        UserDataScope scope = UserDataScope.builder()
                .userId(counselorA.getId())
                .scopeType(ScopeType.SELF)
                .isSelfScopeOnly(true)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);

        LeadRequest request = LeadRequest.builder()
                .fullName("John Doe")
                .phoneNumber("9876543210")
                .departmentId(deptB.getId()) // Conflicting department B
                .build();
        when(leadMapper.toEntity(request)).thenReturn(new Lead());

        leadService.create(request);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        Lead saved = captor.getValue();

        assertEquals(counselorA, saved.getAssignedTo());
        assertEquals(deptA.getId(), saved.getDepartment().getId()); // Guaranteed Department A
    }

    @Test
    @DisplayName("TEST 3: Admin reassigns lead across departments -> department synchronizes to new assignee's department")
    void test3_AdminReassignsAcrossDepartments_DepartmentSynchronizes() throws Exception {
        mockSecurityContext(adminUser);

        Lead lead = Lead.builder()
                .leadCode("LEAD-001")
                .assignedTo(counselorA)
                .department(deptA)
                .build();
        lead.setId(UUID.randomUUID());

        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        UserDataScope adminScope = UserDataScope.builder()
                .userId(adminUser.getId())
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(adminScope);

        LeadAssignmentRequest assignRequest = LeadAssignmentRequest.builder()
                .assignedToUserId(counselorB.getId())
                .remarks("Reassigning to Counselor B in Dept B")
                .build();

        leadAssignmentService.assignLead(lead.getId(), assignRequest);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        Lead saved = captor.getValue();

        assertEquals(counselorB, saved.getAssignedTo());
        assertEquals(deptB.getId(), saved.getDepartment().getId());
    }

    @Test
    @DisplayName("TEST 4: Admin creates lead with User A (Dept A) and conflicting Department B -> sets Department A")
    void test4_AdminCreatesLead_AssignedUserIsSourceOfTruthForDepartment() throws Exception {
        mockSecurityContext(adminUser);

        UserDataScope adminScope = UserDataScope.builder()
                .userId(adminUser.getId())
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(adminScope);

        LeadRequest request = LeadRequest.builder()
                .fullName("Jane Doe")
                .phoneNumber("9876543211")
                .assignedToUserId(counselorA.getId())
                .departmentId(deptB.getId()) // Conflicting Department B
                .build();
        when(leadMapper.toEntity(request)).thenReturn(new Lead());

        leadService.create(request);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        Lead saved = captor.getValue();

        assertEquals(counselorA, saved.getAssignedTo());
        assertEquals(deptA.getId(), saved.getDepartment().getId());
    }

    @Test
    @DisplayName("TEST 5: HOD assignment preserves department consistency")
    void test5_HodAssignment_PreservesDepartmentConsistency() throws Exception {
        mockSecurityContext(hodUser);

        Lead lead = Lead.builder()
                .leadCode("LEAD-005")
                .assignedTo(hodUser)
                .department(deptA)
                .build();
        lead.setId(UUID.randomUUID());

        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        UserDataScope hodScope = UserDataScope.builder()
                .userId(hodUser.getId())
                .scopeType(ScopeType.DEPARTMENT)
                .isHod(true)
                .departmentIds(Set.of(deptA.getId()))
                .departmentUserIds(Set.of(counselorA.getId(), hodUser.getId()))
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(hodScope);

        LeadAssignmentRequest request = LeadAssignmentRequest.builder()
                .assignedToUserId(counselorA.getId())
                .remarks("HOD assigning lead to counselor in same dept")
                .build();

        leadAssignmentService.assignLead(lead.getId(), request);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        Lead saved = captor.getValue();

        assertEquals(counselorA, saved.getAssignedTo());
        assertEquals(deptA.getId(), saved.getDepartment().getId());
    }

    @Test
    @DisplayName("TEST 6: Bulk resolution sets each lead's department to its own assigned user's department")
    void test6_BulkResolver_ResolvesCorrectDepartmentPerUser() {
        Department deptForUserA = LeadDepartmentResolver.resolveDepartmentForUser(counselorA, deptB);
        Department deptForUserB = LeadDepartmentResolver.resolveDepartmentForUser(counselorB, deptA);

        assertEquals(deptA.getId(), deptForUserA.getId());
        assertEquals(deptB.getId(), deptForUserB.getId());
    }

    @Test
    @DisplayName("TEST 7: Status change automatically updates lastContactedAt to current server timestamp")
    void test7_StatusChange_UpdatesLastContactedAt() throws Exception {
        mockSecurityContext(adminUser);

        LocalDateTime oldTimestamp = LocalDateTime.now().minusDays(5);
        Lead lead = Lead.builder()
                .leadCode("LEAD-007")
                .currentStatus(statusNew)
                .lastContactedAt(oldTimestamp)
                .assignedTo(counselorA)
                .department(deptA)
                .build();
        lead.setId(UUID.randomUUID());

        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        UserDataScope adminScope = UserDataScope.builder()
                .userId(adminUser.getId())
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(adminScope);

        LeadStatusChangeRequest request = LeadStatusChangeRequest.builder()
                .newStatusId(statusContacted.getId())
                .feedback("Spoke with lead, agreed to register")
                .build();

        leadService.changeStatus(lead.getId(), request);

        verify(leadStatusTransitionService).executeStatusTransition(eq(lead), eq(statusContacted), eq(adminUser), eq("Spoke with lead, agreed to register"));
        assertEquals(statusContacted, lead.getCurrentStatus());
        assertNotNull(lead.getLastContactedAt());
        assertTrue(lead.getLastContactedAt().isAfter(oldTimestamp));
    }

    @Test
    @DisplayName("TEST 8: Calling status change with identical status does NOT modify lastContactedAt")
    void test8_IdenticalStatusChange_DoesNotModifyLastContactedAt() throws Exception {
        mockSecurityContext(adminUser);

        LocalDateTime originalTimestamp = LocalDateTime.now().minusDays(3);
        Lead lead = Lead.builder()
                .leadCode("LEAD-008")
                .currentStatus(statusNew)
                .lastContactedAt(originalTimestamp)
                .assignedTo(counselorA)
                .department(deptA)
                .build();
        lead.setId(UUID.randomUUID());

        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        UserDataScope adminScope = UserDataScope.builder()
                .userId(adminUser.getId())
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(adminScope);

        LeadStatusChangeRequest request = LeadStatusChangeRequest.builder()
                .newStatusId(statusNew.getId()) // Same status
                .feedback("Adding note without status transition")
                .build();

        leadService.changeStatus(lead.getId(), request);

        assertEquals(statusNew, lead.getCurrentStatus());
        assertEquals(originalTimestamp, lead.getLastContactedAt());
    }

    @Test
    @DisplayName("TEST 9: Direct lead update with conflicting department is overridden to assigned user's department")
    void test9_DirectUpdate_DepartmentIsSynchronizedWithAssignedUser() throws Exception {
        mockSecurityContext(adminUser);

        Lead lead = Lead.builder()
                .leadCode("LEAD-009")
                .assignedTo(counselorA)
                .department(deptA)
                .build();
        lead.setId(UUID.randomUUID());

        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        UserDataScope adminScope = UserDataScope.builder()
                .userId(adminUser.getId())
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(adminScope);

        LeadRequest updateRequest = LeadRequest.builder()
                .fullName("Updated Lead Name")
                .phoneNumber("9876543210")
                .assignedToUserId(counselorA.getId())
                .departmentId(deptB.getId()) // Conflicting department B
                .build();

        leadService.update(lead.getId(), updateRequest);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        Lead saved = captor.getValue();

        assertEquals(counselorA, saved.getAssignedTo());
        assertEquals(deptA.getId(), saved.getDepartment().getId());
    }

    @Test
    @DisplayName("TEST 10: Unassigned Lead (assignedTo == null) preserves requested department")
    void test10_UnassignedLead_PreservesRequestedDepartment() throws Exception {
        mockSecurityContext(adminUser);

        UserDataScope adminScope = UserDataScope.builder()
                .userId(adminUser.getId())
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(adminScope);

        LeadRequest request = LeadRequest.builder()
                .fullName("Unassigned Student")
                .phoneNumber("9876543215")
                .departmentId(deptA.getId())
                .assignedToUserId(null)
                .build();
        when(leadMapper.toEntity(request)).thenReturn(new Lead());

        leadService.create(request);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        Lead saved = captor.getValue();

        assertNull(saved.getAssignedTo());
        assertNotNull(saved.getDepartment());
        assertEquals(deptA.getId(), saved.getDepartment().getId());
    }
}
