package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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

import com.app.datadistribution.config.UserManagementProperties;
import com.app.datadistribution.dto.user.UserBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.user.UserBulkUploadResponseDTO;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.security.UserSecurityValidator;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.impl.UserBulkUploadServiceImpl;
import com.app.datadistribution.service.interfaces.IActivityLogService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

@ExtendWith(MockitoExtension.class)
public class UserBulkUploadServiceTest {

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

    private Department deptCse;
    private Department deptSom;
    private Role roleCounselor;
    private Role roleHod;
    private Role roleAdmin;

    @BeforeEach
    void setUp() {
        deptCse = Department.builder()
                .name("Computer Science & Engineering")
                .code("CSE")
                .description("CSE Department")
                .active(true)
                .build();
        deptCse.setId(UUID.randomUUID());

        deptSom = Department.builder()
                .name("School of Management")
                .code("SOM")
                .description("Management Department")
                .active(true)
                .build();
        deptSom.setId(UUID.randomUUID());

        roleCounselor = Role.builder()
                .name(RoleType.COUNSELOR.name())
                .description("Counselor role")
                .build();
        roleCounselor.setId(UUID.randomUUID());

        roleHod = Role.builder()
                .name(RoleType.HOD.name())
                .description("HOD role")
                .build();
        roleHod.setId(UUID.randomUUID());

        roleAdmin = Role.builder()
                .name(RoleType.ADMIN.name())
                .description("Admin role")
                .build();
        roleAdmin.setId(UUID.randomUUID());
    }

    private byte[] createSampleWorkbookBytes(String sheetName, String[] headers, String[][] rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
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

    @Test
    @DisplayName("Should successfully generate User Excel template with canonical headers and instructions sheet")
    void testGenerateTemplate() throws Exception {
        byte[] bytes = bulkUploadService.generateTemplate();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("Users"));
            assertNotNull(workbook.getSheet("Instructions"));

            Sheet userSheet = workbook.getSheet("Users");
            Row headerRow = userSheet.getRow(0);
            assertNotNull(headerRow);

            StringBuilder allHeaders = new StringBuilder();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                String val = headerRow.getCell(i).getStringCellValue();
                allHeaders.append(val).append(" | ");
            }

            // Verify canonical Department Name header is present and NO internal IDs are exposed
            assertTrue(allHeaders.toString().contains("Department Name *"));
            assertFalse(allHeaders.toString().contains("Department ID"));
            assertFalse(allHeaders.toString().contains("departmentId"));
            assertFalse(allHeaders.toString().contains("User ID"));
            assertFalse(allHeaders.toString().contains("Database ID"));
        }
    }

    @Test
    @DisplayName("Should validate valid user rows and resolve departments and roles by name")
    void testValidateExcel_Success() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCse, deptSom));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor, roleHod));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        String[][] data = {
                {"1", "Rahul", "Sharma", "rahul.sharma@example.com", "9876543210", "rahul.sharma", "COUNSELOR", "Computer Science & Engineering", "ACTIVE", "User@123", ""},
                {"2", "Priya", "Patel", "priya.patel@example.com", "9876543211", "priya.patel", "HOD", "School of Management", "ACTIVE", "User@123", "FULL_ACCESS"}
        };

        byte[] bytes = createSampleWorkbookBytes("Users", UserBulkUploadServiceImpl.TEMPLATE_HEADERS, data);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

        assertNotNull(preview);
        assertTrue(preview.isSuccess());
        assertEquals(2, preview.getTotalRows());
        assertEquals(2, preview.getValidRows());
        assertEquals(0, preview.getErrorRows());
        assertTrue(preview.isCanImport());
        assertFalse(preview.isErrorFileAvailable());
        assertEquals(2, preview.getRows().size());
        assertEquals("Rahul", preview.getRows().get(0).getFirstName());
        assertEquals("Computer Science & Engineering", preview.getRows().get(0).getDepartmentName());
    }

    @Test
    @DisplayName("Should reject row with DEPARTMENT_NOT_FOUND when Department Name does not match any existing department")
    void testValidateExcel_DepartmentNotFound() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCse, deptSom));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        String[][] data = {
                {"1", "Rahul", "Sharma", "rahul.sharma@example.com", "9876543210", "rahul.sharma", "COUNSELOR", "Unknown Engineering Dept", "ACTIVE", "User@123", ""}
        };

        byte[] bytes = createSampleWorkbookBytes("Users", UserBulkUploadServiceImpl.TEMPLATE_HEADERS, data);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

        assertNotNull(preview);
        assertEquals(1, preview.getTotalRows());
        assertEquals(0, preview.getValidRows());
        assertEquals(1, preview.getErrorRows());
        assertFalse(preview.isCanImport());
        assertTrue(preview.isErrorFileAvailable());
        assertEquals(1, preview.getErrors().size());
        assertEquals("DEPARTMENT_NOT_FOUND", preview.getErrors().get(0).getErrorCode());
        assertTrue(preview.getErrors().get(0).getErrorMessage().contains("Unknown Engineering Dept"));

        // Verify that no department was created automatically
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    @DisplayName("Should reject duplicate emails in the same Excel sheet")
    void testValidateExcel_DuplicateInSheet() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCse));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        String[][] data = {
                {"1", "Rahul", "Sharma", "rahul@example.com", "9876543210", "rahul.1", "COUNSELOR", "Computer Science & Engineering", "ACTIVE", "", ""},
                {"2", "Rahul", "Varma", "rahul@example.com", "9876543211", "rahul.2", "COUNSELOR", "Computer Science & Engineering", "ACTIVE", "", ""}
        };

        byte[] bytes = createSampleWorkbookBytes("Users", UserBulkUploadServiceImpl.TEMPLATE_HEADERS, data);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

        assertNotNull(preview);
        assertEquals(2, preview.getTotalRows());
        assertEquals(1, preview.getValidRows());
        assertEquals(1, preview.getErrorRows());
        assertEquals("DUPLICATE_EMAIL_IN_SHEET", preview.getErrors().get(0).getErrorCode());
    }

    @Test
    @DisplayName("Should reject existing email in database")
    void testValidateExcel_DuplicateInDatabase() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCse));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        String[][] data = {
                {"1", "Rahul", "Sharma", "existing@example.com", "9876543210", "rahul.1", "COUNSELOR", "Computer Science & Engineering", "ACTIVE", "", ""}
        };

        byte[] bytes = createSampleWorkbookBytes("Users", UserBulkUploadServiceImpl.TEMPLATE_HEADERS, data);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

        assertNotNull(preview);
        assertEquals(1, preview.getErrorRows());
        assertEquals("EMAIL_ALREADY_EXISTS", preview.getErrors().get(0).getErrorCode());
    }

    @Test
    @DisplayName("Should reject admin role if admin creation is disabled in configuration")
    void testValidateExcel_AdminCreationDisabled() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCse));
        when(roleRepository.findAll()).thenReturn(List.of(roleAdmin));
        when(userSecurityValidator.isPrivilegedRole("ADMIN")).thenReturn(true);
        when(userManagementProperties.isAllowAdminCreation()).thenReturn(false);

        String[][] data = {
                {"1", "System", "Admin", "admin@example.com", "9876543210", "sysadmin", "ADMIN", "", "ACTIVE", "", ""}
        };

        byte[] bytes = createSampleWorkbookBytes("Users", UserBulkUploadServiceImpl.TEMPLATE_HEADERS, data);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

        assertNotNull(preview);
        assertEquals(1, preview.getErrorRows());
        assertEquals("ADMIN_CREATION_DISABLED", preview.getErrors().get(0).getErrorCode());
    }

    @Test
    @DisplayName("Should reject user assignment outside caller's scoped department when caller is restricted HOD")
    void testValidateExcel_CallerDepartmentScopeRestricted() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCse, deptSom));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));

        // Caller is scoped only to deptCse
        UserDataScope hodScope = UserDataScope.builder()
                .isAdmin(false)
                .isHod(true)
                .departmentIds(Set.of(deptCse.getId()))
                .build();
        when(dataScopeService.getScopeForCurrentUser()).thenReturn(hodScope);

        // Row tries to assign to deptSom
        String[][] data = {
                {"1", "Rahul", "Sharma", "rahul@example.com", "9876543210", "rahul.sharma", "COUNSELOR", "School of Management", "ACTIVE", "", ""}
        };

        byte[] bytes = createSampleWorkbookBytes("Users", UserBulkUploadServiceImpl.TEMPLATE_HEADERS, data);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

        assertNotNull(preview);
        assertEquals(1, preview.getErrorRows());
        assertEquals("UNAUTHORIZED_DEPARTMENT_SCOPE", preview.getErrors().get(0).getErrorCode());
    }

    @Test
    @DisplayName("Should successfully bulk upload valid users and associate them with existing Department entities")
    void testBulkUpload_Success() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCse));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd-123");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String[][] data = {
                {"1", "Rahul", "Sharma", "rahul.sharma@example.com", "9876543210", "rahul.sharma", "COUNSELOR", "Computer Science & Engineering", "ACTIVE", "", ""}
        };

        byte[] bytes = createSampleWorkbookBytes("Users", UserBulkUploadServiceImpl.TEMPLATE_HEADERS, data);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadResponseDTO response = bulkUploadService.bulkUpload(file);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(1, response.getSuccessfulRows());
        assertEquals(0, response.getFailedRows());
        assertEquals(1, response.getCreatedRecords());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("Rahul", savedUser.getFirstName());
        assertEquals("Sharma", savedUser.getLastName());
        assertEquals("rahul.sharma@example.com", savedUser.getEmail());
        assertEquals("hashed-pwd-123", savedUser.getPassword());
        assertNotNull(savedUser.getDepartments());
        assertEquals(1, savedUser.getDepartments().size());
        assertEquals("Computer Science & Engineering", savedUser.getDepartments().iterator().next().getName());
        assertEquals(deptCse.getId(), savedUser.getDepartments().iterator().next().getId());
    }

    @Test
    @DisplayName("Should retrieve downloadable error spreadsheet after failed validation")
    void testGetErrorFile_SuccessAndNotFound() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCse));
        when(roleRepository.findAll()).thenReturn(List.of(roleCounselor));

        // Invalid row with missing required fields
        String[][] data = {
                {"1", "", "", "invalid-email", "", "", "", "Unknown Dept", "ACTIVE", "", ""}
        };

        byte[] bytes = createSampleWorkbookBytes("Users", UserBulkUploadServiceImpl.TEMPLATE_HEADERS, data);
        MockMultipartFile file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        UserBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertTrue(preview.isErrorFileAvailable());
        assertNotNull(preview.getImportId());

        UUID importUuid = UUID.fromString(preview.getImportId());
        byte[] errorFileContent = bulkUploadService.getErrorFile(importUuid);
        assertNotNull(errorFileContent);
        assertTrue(errorFileContent.length > 0);

        // Test non-existent import ID throws ResourcesNotFoundException
        UUID nonExistentId = UUID.randomUUID();
        assertThrows(ResourcesNotFoundException.class, () -> bulkUploadService.getErrorFile(nonExistentId));
    }
}
