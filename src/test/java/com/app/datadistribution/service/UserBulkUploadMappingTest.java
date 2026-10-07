package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.datadistribution.config.UserManagementProperties;
import com.app.datadistribution.dto.user.UserBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.user.UserBulkUploadResponseDTO;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.security.UserSecurityValidator;
import com.app.datadistribution.service.impl.UserBulkUploadServiceImpl;
import com.app.datadistribution.service.interfaces.IActivityLogService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
public class UserBulkUploadMappingTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private IUserDataScopeService dataScopeService;

    @Mock
    private UserSecurityValidator userSecurityValidator;

    @Mock
    private UserManagementProperties userManagementProperties;

    @Mock
    private IActivityLogService activityLogService;

    @InjectMocks
    private UserBulkUploadServiceImpl bulkUploadService;

    private Department deptCommerce;
    private Department deptManagement;
    private Role roleCounselor;

    @BeforeEach
    void setUp() {
        deptCommerce = Department.builder()
                .name("Commerce")
                .code("COM")
                .description("Department of Commerce")
                .active(true)
                .build();
        deptCommerce.setId(UUID.randomUUID());

        deptManagement = Department.builder()
                .name("Management")
                .code("MGT")
                .description("Department of Management")
                .active(true)
                .build();
        deptManagement.setId(UUID.randomUUID());

        roleCounselor = Role.builder()
                .name(RoleType.COUNSELOR.name())
                .description("Counselor role")
                .build();
        roleCounselor.setId(UUID.randomUUID());
    }

    private byte[] createWorkbookBytes(String[] headers, String[][] rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Users");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            if (rows != null) {
                for (int r = 0; r < rows.length; r++) {
                    Row row = sheet.createRow(r + 1);
                    for (int c = 0; c < rows[r].length; c++) {
                        row.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // =========================================================================
    // SECTION 34 TESTS
    // =========================================================================

    @Test
    @DisplayName("Test 1 — Normal order: Name | Email | Mobile | Role | Department Name")
    void test1_NormalOrderMapping() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCommerce));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        String[] headers = {"Name", "Email", "Mobile", "Role", "Department Name"};
        String[][] rows = {
                {"Rahul Sharma", "rahul@gmail.com", "9876543210", "COUNSELLOR", "Commerce"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

        assertNotNull(preview);
        assertEquals(0, preview.getErrorRows());
        assertEquals(1, preview.getValidRows());

        var rowDto = preview.getRows().get(0);
        assertEquals("Rahul Sharma", rowDto.getName());
        assertEquals("rahul@gmail.com", rowDto.getEmail());
        assertEquals("9876543210", rowDto.getPhone());
        assertEquals("COUNSELOR", rowDto.getRoleName());
        assertEquals("Commerce", rowDto.getDepartmentName());
    }

    @Test
    @DisplayName("Test 2 — Reordered columns: Department Name | Mobile | Role | Name | Email")
    void test2_ReorderedColumnsMapping() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCommerce));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        String[] headers = {"Department Name", "Mobile", "Role", "Name", "Email"};
        String[][] rows = {
                {"Commerce", "9876543210", "COUNSELLOR", "Rahul Sharma", "rahul@gmail.com"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

        assertNotNull(preview);
        assertEquals(0, preview.getErrorRows());
        assertEquals(1, preview.getValidRows());

        var rowDto = preview.getRows().get(0);
        assertEquals("Rahul Sharma", rowDto.getName());
        assertEquals("rahul@gmail.com", rowDto.getEmail());
        assertEquals("9876543210", rowDto.getPhone());
        assertEquals("COUNSELOR", rowDto.getRoleName());
        assertEquals("Commerce", rowDto.getDepartmentName());
    }

    @Test
    @DisplayName("Test 3 — Extra optional columns: Ensure mapping remains correct")
    void test3_ExtraOptionalColumns() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCommerce));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        String[] headers = {"S.No.", "Name", "Email", "Status", "Mobile", "Role", "Department Name", "Password"};
        String[][] rows = {
                {"1", "Rahul Sharma", "rahul@gmail.com", "ACTIVE", "9876543210", "COUNSELLOR", "Commerce", "Custom@123"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

        assertNotNull(preview);
        assertEquals(0, preview.getErrorRows());
        assertEquals(1, preview.getValidRows());

        var rowDto = preview.getRows().get(0);
        assertEquals("Rahul Sharma", rowDto.getName());
        assertEquals("rahul@gmail.com", rowDto.getEmail());
        assertEquals("9876543210", rowDto.getPhone());
        assertEquals("COUNSELOR", rowDto.getRoleName());
        assertEquals("Commerce", rowDto.getDepartmentName());
        assertTrue(rowDto.isActive());
        assertEquals("Custom@123", rowDto.getPassword());
    }

    @Test
    @DisplayName("Test 4 — Header whitespace: '   Department    Name   ' is safely normalized")
    void test4_HeaderWhitespaceNormalization() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCommerce));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        String[] headers = {"  Name  ", "  Email   ", " Mobile  ", "  Role  ", "   Department    Name   "};
        String[][] rows = {
                {"Rahul Sharma", "rahul@gmail.com", "9876543210", "COUNSELLOR", "Commerce"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

        assertNotNull(preview);
        assertEquals(0, preview.getErrorRows());
        assertEquals(1, preview.getValidRows());
        assertEquals("Commerce", preview.getRows().get(0).getDepartmentName());
    }

    @Test
    @DisplayName("Test 5 — Header case differences: 'department name' in lowercase is matched")
    void test5_HeaderCaseNormalization() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCommerce));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        String[] headers = {"name", "email", "mobile", "role", "department name"};
        String[][] rows = {
                {"Rahul Sharma", "rahul@gmail.com", "9876543210", "COUNSELLOR", "Commerce"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

        assertNotNull(preview);
        assertEquals(0, preview.getErrorRows());
        assertEquals(1, preview.getValidRows());
        assertEquals("Commerce", preview.getRows().get(0).getDepartmentName());
    }

    @Test
    @DisplayName("Test 6 — Duplicate header in Excel must fail validation with DUPLICATE_HEADER")
    void test6_DuplicateHeaderRejection() throws Exception {
        String[] headers = {"Name", "Email", "Name", "Role", "Department Name"};
        String[][] rows = {
                {"Rahul", "rahul@gmail.com", "Sharma", "COUNSELLOR", "Commerce"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bulkUploadService.validateExcel(file));
        assertTrue(ex.getMessage().contains("Duplicate header"));
    }

    @Test
    @DisplayName("Test 7 — Missing required header must fail validation with missing required header message")
    void test7_MissingRequiredHeaderRejection() throws Exception {
        // Missing 'Department Name'
        String[] headers = {"Name", "Email", "Mobile", "Role"};
        String[][] rows = {
                {"Rahul", "rahul@gmail.com", "9876543210", "COUNSELLOR"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bulkUploadService.validateExcel(file));
        assertTrue(ex.getMessage().contains("missing required column: 'Department Name *'"));
    }

    @Test
    @DisplayName("Test 8 — Unknown header must fail validation with unknown column message")
    void test8_UnknownHeaderRejection() throws Exception {
        // Typo: 'Department Nam'
        String[] headers = {"Name", "Email", "Mobile", "Role", "Department Nam"};
        String[][] rows = {
                {"Rahul", "rahul@gmail.com", "9876543210", "COUNSELLOR", "Commerce"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bulkUploadService.validateExcel(file));
        assertTrue(ex.getMessage().contains("Unknown column 'Department Nam'"));
    }

    @Test
    @DisplayName("Test 9 — Invalid Department returns DEPARTMENT_NOT_FOUND error")
    void test9_InvalidDepartment() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCommerce));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        String[] headers = {"Name", "Email", "Mobile", "Role", "Department Name"};
        String[][] rows = {
                {"Rahul", "rahul@gmail.com", "9876543210", "COUNSELLOR", "Unknown Nonexistent Dept"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertNotNull(preview);
        assertEquals(1, preview.getErrorRows());
        assertEquals("DEPARTMENT_NOT_FOUND", preview.getErrors().get(0).getErrorCode());
    }

    @Test
    @DisplayName("Test 10 — Invalid Role returns ROLE_NOT_FOUND error")
    void test10_InvalidRole() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCommerce));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));

        String[] headers = {"Name", "Email", "Mobile", "Role", "Department Name"};
        String[][] rows = {
                {"Rahul", "rahul@gmail.com", "9876543210", "INVALID_ROLE_XYZ", "Commerce"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertNotNull(preview);
        assertEquals(1, preview.getErrorRows());
        assertEquals("ROLE_NOT_FOUND", preview.getErrors().get(0).getErrorCode());
    }

    // =========================================================================
    // SECTION 35 INTEGRATION TEST
    // =========================================================================

    @Test
    @DisplayName("Section 35 Integration Test: Department Name | Role | Email | Name | Mobile persists correctly and produces exact same result when reordered")
    void testSection35_IntegrationVerification() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCommerce, deptManagement));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // --- Run 1: Columns Order A ---
        // Department Name | Role | Email | Name | Mobile
        String[] headersA = {"Department Name", "Role", "Email", "Name", "Mobile"};
        String[][] dataA = {
                {"Commerce", "COUNSELLOR", "test1@example.com", "Rahul", "9876543210"},
                {"Management", "COUNSELLOR", "test2@example.com", "Amit", "9876543211"}
        };

        byte[] bytesA = createWorkbookBytes(headersA, dataA);
        MockMultipartFile fileA = new MockMultipartFile("file", "usersA.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytesA);

        UserBulkUploadResponseDTO responseA = bulkUploadService.bulkUpload(fileA);
        assertTrue(responseA.isSuccess());
        assertEquals(2, responseA.getSuccessfulRows());
        assertEquals(0, responseA.getFailedRows());

        ArgumentCaptor<User> captorA = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(captorA.capture());
        List<User> savedUsersA = captorA.getAllValues();

        // Verify Rahul -> Commerce
        User user1A = savedUsersA.get(0);
        assertEquals("Rahul", user1A.getFirstName());
        assertEquals("test1@example.com", user1A.getEmail());
        assertEquals("9876543210", user1A.getPhone());
        assertEquals("COUNSELOR", user1A.getRoles().iterator().next().getName());
        assertEquals("Commerce", user1A.getDepartments().iterator().next().getName());

        // Verify Amit -> Management
        User user2A = savedUsersA.get(1);
        assertEquals("Amit", user2A.getFirstName());
        assertEquals("test2@example.com", user2A.getEmail());
        assertEquals("9876543211", user2A.getPhone());
        assertEquals("COUNSELOR", user2A.getRoles().iterator().next().getName());
        assertEquals("Management", user2A.getDepartments().iterator().next().getName());

        // --- Run 2: Columns Order B (Reordered) ---
        // Mobile | Name | Email | Department Name | Role
        String[] headersB = {"Mobile", "Name", "Email", "Department Name", "Role"};
        String[][] dataB = {
                {"9876543210", "Rahul", "test1@example.com", "Commerce", "COUNSELLOR"},
                {"9876543211", "Amit", "test2@example.com", "Management", "COUNSELLOR"}
        };

        byte[] bytesB = createWorkbookBytes(headersB, dataB);
        MockMultipartFile fileB = new MockMultipartFile("file", "usersB.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytesB);

        UserBulkUploadResponseDTO responseB = bulkUploadService.bulkUpload(fileB);
        assertTrue(responseB.isSuccess());
        assertEquals(2, responseB.getSuccessfulRows());
        assertEquals(0, responseB.getFailedRows());

        ArgumentCaptor<User> captorB = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(4)).save(captorB.capture());
        List<User> allSaved = captorB.getAllValues();
        List<User> savedUsersB = allSaved.subList(2, 4);

        User user1B = savedUsersB.get(0);
        assertEquals(user1A.getFirstName(), user1B.getFirstName());
        assertEquals(user1A.getEmail(), user1B.getEmail());
        assertEquals(user1A.getPhone(), user1B.getPhone());
        assertEquals(user1A.getRoles().iterator().next().getName(), user1B.getRoles().iterator().next().getName());
        assertEquals(user1A.getDepartments().iterator().next().getName(), user1B.getDepartments().iterator().next().getName());

        User user2B = savedUsersB.get(1);
        assertEquals(user2A.getFirstName(), user2B.getFirstName());
        assertEquals(user2A.getEmail(), user2B.getEmail());
        assertEquals(user2A.getPhone(), user2B.getPhone());
        assertEquals(user2A.getRoles().iterator().next().getName(), user2B.getRoles().iterator().next().getName());
        assertEquals(user2A.getDepartments().iterator().next().getName(), user2B.getDepartments().iterator().next().getName());
    }
}
