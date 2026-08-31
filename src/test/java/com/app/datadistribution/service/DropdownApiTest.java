package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.app.datadistribution.dto.dropdown.CourseDropdownResponse;
import com.app.datadistribution.dto.dropdown.DropdownOptionResponse;
import com.app.datadistribution.dto.dropdown.DropdownPageResponse;
import com.app.datadistribution.dto.dropdown.LeadDropdownResponse;
import com.app.datadistribution.dto.dropdown.LeadStatusDropdownResponse;
import com.app.datadistribution.dto.dropdown.UserDropdownResponse;
import com.app.datadistribution.entity.Board;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Grade;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.SentimentCategory;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.repository.BoardRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.GradeRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.PermissionRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.DropdownServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DropdownApiTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private LeadSourceRepository leadSourceRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseTypeRepository courseTypeRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private IUserDataScopeService userDataScopeService;
    @Mock
    private ILeadDataScopeService leadDataScopeService;

    @InjectMocks
    private DropdownServiceImpl dropdownService;

    private Department deptA;
    private Department deptB;
    private User counselorA;
    private User counselorB;
    private User hodUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        deptA = Department.builder().name("Department A").code("DEP_A").active(true).build();
        deptA.setId(UUID.randomUUID());

        deptB = Department.builder().name("Department B").code("DEP_B").active(true).build();
        deptB.setId(UUID.randomUUID());

        counselorA = User.builder().firstName("John").lastName("Doe").username("counselorA").email("counselora@test.com")
                .departments(new HashSet<>(Set.of(deptA))).active(true).build();
        counselorA.setId(UUID.randomUUID());

        counselorB = User.builder().firstName("Jane").lastName("Smith").username("counselorB").email("counselorb@test.com")
                .departments(new HashSet<>(Set.of(deptB))).active(true).build();
        counselorB.setId(UUID.randomUUID());

        hodUser = User.builder().firstName("Hod").lastName("User").username("hodUser").email("hod@test.com")
                .departments(new HashSet<>(Set.of(deptA))).active(true).build();
        hodUser.setId(UUID.randomUUID());

        adminUser = User.builder().firstName("Admin").lastName("User").username("adminUser").email("admin@test.com")
                .departments(new HashSet<>()).active(true).build();
        adminUser.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Admin Users Dropdown: returns all active users without sensitive auth fields")
    void testGetUsersDropdown_Admin_ReturnsAllActiveUsers() throws Exception {
        UserDataScope adminScope = UserDataScope.builder()
                .userId(adminUser.getId())
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .build();
        when(userDataScopeService.getScopeForCurrentUser()).thenReturn(adminScope);
        when(userRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(counselorA, counselorB, hodUser));

        List<UserDropdownResponse> result = dropdownService.getUsersDropdown(null, null, null);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("John Doe", result.get(0).getName());
        assertEquals("counselorA", result.get(0).getUsername());
        assertEquals("counselora@test.com", result.get(0).getEmail());
    }

    @Test
    @DisplayName("HOD Users Dropdown: allows querying authorized department")
    void testGetUsersDropdown_HOD_AuthorizedDepartment_ReturnsDepartmentUsers() throws Exception {
        UserDataScope hodScope = UserDataScope.builder()
                .userId(hodUser.getId())
                .scopeType(ScopeType.DEPARTMENT)
                .isHod(true)
                .departmentIds(Set.of(deptA.getId()))
                .build();
        when(userDataScopeService.getScopeForCurrentUser()).thenReturn(hodScope);
        when(userRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(counselorA, hodUser));

        List<UserDropdownResponse> result = dropdownService.getUsersDropdown(deptA.getId(), null, null);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("HOD Users Dropdown IDOR Defense: querying unauthorized department throws ForbiddenException")
    void testGetUsersDropdown_HOD_UnauthorizedDepartment_ThrowsForbidden() throws Exception {
        UserDataScope hodScope = UserDataScope.builder()
                .userId(hodUser.getId())
                .scopeType(ScopeType.DEPARTMENT)
                .isHod(true)
                .departmentIds(Set.of(deptA.getId())) // Only dept A
                .build();
        when(userDataScopeService.getScopeForCurrentUser()).thenReturn(hodScope);

        assertThrows(AccessDeniedException.class, () -> dropdownService.getUsersDropdown(deptB.getId(), null, null));
    }

    @Test
    @DisplayName("Department Dropdown: HOD sees only mapped active departments")
    void testGetDepartmentsDropdown_HOD_ReturnsMappedDepartments() throws Exception {
        UserDataScope hodScope = UserDataScope.builder()
                .userId(hodUser.getId())
                .scopeType(ScopeType.DEPARTMENT)
                .isHod(true)
                .departmentIds(Set.of(deptA.getId()))
                .build();
        when(userDataScopeService.getScopeForCurrentUser()).thenReturn(hodScope);
        when(departmentRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(deptA));

        List<DropdownOptionResponse> result = dropdownService.getDepartmentsDropdown(null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(deptA.getId(), result.get(0).getId());
        assertEquals("Department A", result.get(0).getName());
        assertEquals("DEP_A", result.get(0).getCode());
    }

    @Test
    @DisplayName("Lead Dropdown: uses data scoping and returns lightweight paginated response")
    void testGetLeadsDropdown_ScopedAndPaginated() throws Exception {
        UserDataScope scope = UserDataScope.builder()
                .userId(counselorA.getId())
                .scopeType(ScopeType.SELF)
                .isSelfScopeOnly(true)
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        when(leadDataScopeService.getLeadScopeSpecification(scope)).thenReturn((root, q, cb) -> cb.conjunction());

        Lead lead1 = Lead.builder().leadCode("LEAD-101").fullName("Lead Student").phoneNumber("9876543210").build();
        lead1.setId(UUID.randomUUID());
        Page<Lead> page = new PageImpl<>(List.of(lead1));
        when(leadRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        DropdownPageResponse<LeadDropdownResponse> result = dropdownService.getLeadsDropdown(0, 10, "Student", null, null);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("LEAD-101", result.getContent().get(0).getLeadCode());
        assertEquals("Lead Student", result.getContent().get(0).getFullName());
        assertEquals("9876543210", result.getContent().get(0).getPhoneNumber());
    }

    @Test
    @DisplayName("Lead Statuses Dropdown: filters by sentiment category and active status")
    void testGetLeadStatusesDropdown_FiltersActiveAndSentiment() {
        LeadStatus status1 = LeadStatus.builder().name("Raw").code("RAW").sentimentCategory(SentimentCategory.NEUTRAL).active(true).displayOrder(1).build();
        status1.setId(UUID.randomUUID());
        LeadStatus status2 = LeadStatus.builder().name("Interested").code("INTERESTED").sentimentCategory(SentimentCategory.POSITIVE).active(true).displayOrder(2).build();
        status2.setId(UUID.randomUUID());
        LeadStatus inactiveStatus = LeadStatus.builder().name("Old").code("OLD").active(false).build();

        when(leadStatusRepository.findAll()).thenReturn(List.of(status1, status2, inactiveStatus));

        List<LeadStatusDropdownResponse> result = dropdownService.getLeadStatusesDropdown(SentimentCategory.POSITIVE);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Interested", result.get(0).getName());
        assertEquals("POSITIVE", result.get(0).getSentimentCategory());
    }

    @Test
    @DisplayName("Courses Dropdown: filters by course type and active status")
    void testGetCoursesDropdown_FiltersCourseType() {
        CourseType type1 = CourseType.builder().name("UG").status(Status.ACTIVE).build();
        type1.setId(UUID.randomUUID());
        Course course1 = Course.builder().courseName("B.Tech").courseCode("BTECH").courseType(type1).status(Status.ACTIVE).build();
        course1.setId(UUID.randomUUID());
        Course course2 = Course.builder().courseName("M.Tech").courseCode("MTECH").status(Status.ACTIVE).build();
        course2.setId(UUID.randomUUID());

        when(courseRepository.findAll()).thenReturn(List.of(course1, course2));

        List<CourseDropdownResponse> result = dropdownService.getCoursesDropdown(type1.getId(), null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("B.Tech", result.get(0).getName());
        assertEquals("UG", result.get(0).getCourseTypeName());
    }

    @Test
    @DisplayName("Master Data Dropdowns: Boards, Grades, CourseTypes, Sources, Roles, Permissions")
    void testMasterDataDropdowns_ReturnActiveOnly() {
        Board board = Board.builder().name("CBSE").code("CBSE").active(true).displayOrder(1).build();
        board.setId(UUID.randomUUID());
        when(boardRepository.findAll()).thenReturn(List.of(board));
        List<DropdownOptionResponse> boards = dropdownService.getBoardsDropdown(null);
        assertEquals(1, boards.size());
        assertEquals("CBSE", boards.get(0).getName());

        Grade grade = Grade.builder().name("Grade 12").code("G12").active(true).displayOrder(1).build();
        grade.setId(UUID.randomUUID());
        when(gradeRepository.findAll()).thenReturn(List.of(grade));
        List<DropdownOptionResponse> grades = dropdownService.getGradesDropdown(null);
        assertEquals(1, grades.size());
        assertEquals("Grade 12", grades.get(0).getName());

        CourseType cType = CourseType.builder().name("Post Graduate").status(Status.ACTIVE).build();
        cType.setId(UUID.randomUUID());
        when(courseTypeRepository.findAll()).thenReturn(List.of(cType));
        List<DropdownOptionResponse> types = dropdownService.getCourseTypesDropdown(null);
        assertEquals(1, types.size());
        assertEquals("Post Graduate", types.get(0).getName());

        LeadSource source = LeadSource.builder().name("Google Ads").code("GADS").active(true).build();
        source.setId(UUID.randomUUID());
        when(leadSourceRepository.findAll()).thenReturn(List.of(source));
        List<DropdownOptionResponse> sources = dropdownService.getLeadSourcesDropdown(null);
        assertEquals(1, sources.size());
        assertEquals("Google Ads", sources.get(0).getName());

        Role role = Role.builder().name("COUNSELOR").active(true).build();
        role.setId(UUID.randomUUID());
        when(roleRepository.findAll()).thenReturn(List.of(role));
        List<DropdownOptionResponse> roles = dropdownService.getRolesDropdown(null);
        assertEquals(1, roles.size());
        assertEquals("COUNSELOR", roles.get(0).getName());

        Permission perm = Permission.builder().name("LEAD_READ").active(true).build();
        perm.setId(UUID.randomUUID());
        when(permissionRepository.findAll()).thenReturn(List.of(perm));
        List<DropdownOptionResponse> perms = dropdownService.getPermissionsDropdown(null);
        assertEquals(1, perms.size());
        assertEquals("LEAD_READ", perms.get(0).getName());
    }
}
