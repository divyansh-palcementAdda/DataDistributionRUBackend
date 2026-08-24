package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.HodAccessType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.LeadDataScopeServiceImpl;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadDataScopeServiceTest {

    @Mock
    private IUserDataScopeService dataScopeService;

    @InjectMocks
    private LeadDataScopeServiceImpl leadDataScopeService;

    private UUID adminId;
    private UUID hodId;
    private UUID counselor1Id;
    private UUID counselor2Id;

    private UUID dept1Id;
    private UUID dept2Id;

    private Department dept1;
    private Department dept2;

    private User adminUser;
    private User hodUser;
    private User counselor1;
    private User counselor2;

    private Lead leadDept1Counselor1;
    private Lead leadDept1Counselor2;
    private Lead leadDept2Counselor2;
    private Lead leadDept1Unassigned;
    private Lead leadDept2Unassigned;
    private Lead leadAssignedToHod;
    private Lead leadCreatedByCounselor1;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        hodId = UUID.randomUUID();
        counselor1Id = UUID.randomUUID();
        counselor2Id = UUID.randomUUID();

        dept1Id = UUID.randomUUID();
        dept2Id = UUID.randomUUID();

        dept1 = Department.builder().build();
        dept1.setId(dept1Id);
        dept1.setName("Engineering");

        dept2 = Department.builder().build();
        dept2.setId(dept2Id);
        dept2.setName("Management");

        adminUser = User.builder().build();
        adminUser.setId(adminId);
        adminUser.setUsername("admin");

        hodUser = User.builder().build();
        hodUser.setId(hodId);
        hodUser.setUsername("hod");

        counselor1 = User.builder().build();
        counselor1.setId(counselor1Id);
        counselor1.setUsername("counselor1");

        counselor2 = User.builder().build();
        counselor2.setId(counselor2Id);
        counselor2.setUsername("counselor2");

        leadDept1Counselor1 = Lead.builder()
                .leadCode("LEAD-001")
                .department(dept1)
                .assignedTo(counselor1)
                .createdByUser(adminUser)
                .build();
        leadDept1Counselor1.setId(UUID.randomUUID());

        leadDept1Counselor2 = Lead.builder()
                .leadCode("LEAD-002")
                .department(dept1)
                .assignedTo(counselor2)
                .createdByUser(adminUser)
                .build();
        leadDept1Counselor2.setId(UUID.randomUUID());

        leadDept2Counselor2 = Lead.builder()
                .leadCode("LEAD-003")
                .department(dept2)
                .assignedTo(counselor2)
                .createdByUser(adminUser)
                .build();
        leadDept2Counselor2.setId(UUID.randomUUID());

        leadDept1Unassigned = Lead.builder()
                .leadCode("LEAD-004")
                .department(dept1)
                .assignedTo(null)
                .createdByUser(adminUser)
                .build();
        leadDept1Unassigned.setId(UUID.randomUUID());

        leadDept2Unassigned = Lead.builder()
                .leadCode("LEAD-005")
                .department(dept2)
                .assignedTo(null)
                .createdByUser(adminUser)
                .build();
        leadDept2Unassigned.setId(UUID.randomUUID());

        leadAssignedToHod = Lead.builder()
                .leadCode("LEAD-006")
                .department(null)
                .assignedTo(hodUser)
                .createdByUser(adminUser)
                .build();
        leadAssignedToHod.setId(UUID.randomUUID());

        leadCreatedByCounselor1 = Lead.builder()
                .leadCode("LEAD-007")
                .department(dept2)
                .assignedTo(null)
                .createdByUser(counselor1)
                .build();
        leadCreatedByCounselor1.setId(UUID.randomUUID());
    }

    @Test
    void testAdmin_FullSystemVisibility() throws Exception {
        UserDataScope adminScope = UserDataScope.builder()
                .userId(adminId)
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .build();

        assertTrue(leadDataScopeService.isLeadAccessible(leadDept1Counselor1, adminScope));
        assertTrue(leadDataScopeService.isLeadAccessible(leadDept1Counselor2, adminScope));
        assertTrue(leadDataScopeService.isLeadAccessible(leadDept2Counselor2, adminScope));
        assertTrue(leadDataScopeService.isLeadAccessible(leadDept1Unassigned, adminScope));
        assertTrue(leadDataScopeService.isLeadAccessible(leadDept2Unassigned, adminScope));
        assertTrue(leadDataScopeService.isLeadAccessible(leadAssignedToHod, adminScope));
        assertTrue(leadDataScopeService.isLeadAccessible(leadCreatedByCounselor1, adminScope));

        assertDoesNotThrow(() -> leadDataScopeService.validateLeadReadAccess(leadDept1Counselor1, adminScope));
        assertDoesNotThrow(() -> leadDataScopeService.validateLeadWriteAccess(leadDept1Counselor1, adminScope));
    }

    @Test
    void testHod_SingleDepartmentVisibility() throws Exception {
        UserDataScope hodScope = UserDataScope.builder()
                .userId(hodId)
                .scopeType(ScopeType.DEPARTMENT)
                .isHod(true)
                .departmentIds(Set.of(dept1Id))
                .departmentUserIds(Set.of(counselor1Id, counselor2Id, hodId))
                .hodAccessType(HodAccessType.FULL_ACCESS)
                .build();

        // Mapped Dept leads -> visible
        assertTrue(leadDataScopeService.isLeadAccessible(leadDept1Counselor1, hodScope));
        assertTrue(leadDataScopeService.isLeadAccessible(leadDept1Counselor2, hodScope));
        assertTrue(leadDataScopeService.isLeadAccessible(leadDept1Unassigned, hodScope));

        // Self-assigned lead -> visible even without dept
        assertTrue(leadDataScopeService.isLeadAccessible(leadAssignedToHod, hodScope));

        // Dept 2 leads (unmapped) with Dept 2 unassigned -> not visible
        assertFalse(leadDataScopeService.isLeadAccessible(leadDept2Unassigned, hodScope));
    }

    @Test
    void testHod_MultipleDepartmentsVisibility() throws Exception {
        UserDataScope hodMultiScope = UserDataScope.builder()
                .userId(hodId)
                .scopeType(ScopeType.DEPARTMENT)
                .isHod(true)
                .departmentIds(Set.of(dept1Id, dept2Id))
                .departmentUserIds(Set.of(counselor1Id, counselor2Id, hodId))
                .hodAccessType(HodAccessType.FULL_ACCESS)
                .build();

        assertTrue(leadDataScopeService.isLeadAccessible(leadDept1Counselor1, hodMultiScope));
        assertTrue(leadDataScopeService.isLeadAccessible(leadDept2Counselor2, hodMultiScope));
        assertTrue(leadDataScopeService.isLeadAccessible(leadDept1Unassigned, hodMultiScope));
        assertTrue(leadDataScopeService.isLeadAccessible(leadDept2Unassigned, hodMultiScope));
    }

    @Test
    void testHod_ViewOnly_CannotWriteDepartmentLeads() throws Exception {
        UserDataScope hodViewOnlyScope = UserDataScope.builder()
                .userId(hodId)
                .scopeType(ScopeType.DEPARTMENT)
                .isHod(true)
                .departmentIds(Set.of(dept1Id))
                .departmentUserIds(Set.of(counselor1Id, counselor2Id, hodId))
                .hodAccessType(HodAccessType.VIEW_ONLY)
                .build();

        // Read is allowed
        assertDoesNotThrow(() -> leadDataScopeService.validateLeadReadAccess(leadDept1Counselor1, hodViewOnlyScope));

        // Write on department lead (not assigned/created by HOD) throws UnauthorizedException
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> leadDataScopeService.validateLeadWriteAccess(leadDept1Counselor1, hodViewOnlyScope));
        assertTrue(ex.getMessage().contains("VIEW_ONLY"));

        // Write on own lead is allowed
        assertDoesNotThrow(() -> leadDataScopeService.validateLeadWriteAccess(leadAssignedToHod, hodViewOnlyScope));
    }

    @Test
    void testCounselor_OnlySelfAssignedOrCreatedLeads() throws Exception {
        UserDataScope counselorScope = UserDataScope.builder()
                .userId(counselor1Id)
                .scopeType(ScopeType.SELF)
                .departmentIds(Set.of(dept1Id))
                .departmentUserIds(Set.of(counselor1Id, counselor2Id))
                .build();

        // Own assigned lead -> accessible
        assertTrue(leadDataScopeService.isLeadAccessible(leadDept1Counselor1, counselorScope));
        assertDoesNotThrow(() -> leadDataScopeService.validateLeadReadAccess(leadDept1Counselor1, counselorScope));
        assertDoesNotThrow(() -> leadDataScopeService.validateLeadWriteAccess(leadDept1Counselor1, counselorScope));

        // Own created lead -> accessible
        assertTrue(leadDataScopeService.isLeadAccessible(leadCreatedByCounselor1, counselorScope));

        // Other counselor's lead in SAME department -> NOT accessible
        assertFalse(leadDataScopeService.isLeadAccessible(leadDept1Counselor2, counselorScope));
        assertThrows(UnauthorizedException.class,
                () -> leadDataScopeService.validateLeadReadAccess(leadDept1Counselor2, counselorScope));

        // Other counselor's lead in DIFFERENT department -> NOT accessible
        assertFalse(leadDataScopeService.isLeadAccessible(leadDept2Counselor2, counselorScope));

        // Unassigned lead in same department -> NOT accessible
        assertFalse(leadDataScopeService.isLeadAccessible(leadDept1Unassigned, counselorScope));
    }

    @Test
    void testDeletedLead_NeverAccessible() {
        Lead deletedLead = Lead.builder()
                .leadCode("LEAD-DEL")
                .department(dept1)
                .assignedTo(adminUser)
                .build();
        deletedLead.setId(UUID.randomUUID());
        deletedLead.setDeleted(true);

        UserDataScope adminScope = UserDataScope.builder()
                .userId(adminId)
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .build();

        assertFalse(leadDataScopeService.isLeadAccessible(deletedLead, adminScope));
    }
}
