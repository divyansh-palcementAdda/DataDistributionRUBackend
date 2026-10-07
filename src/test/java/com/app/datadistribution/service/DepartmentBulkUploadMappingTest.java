package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.datadistribution.dto.department.DepartmentBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.department.DepartmentBulkUploadResponseDTO;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.service.impl.DepartmentBulkUploadServiceImpl;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
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

@ExtendWith(MockitoExtension.class)
public class DepartmentBulkUploadMappingTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentBulkUploadServiceImpl bulkUploadService;

    private Department deptCse;

    @BeforeEach
    void setUp() {
        deptCse = Department.builder()
                .name("Computer Science & Engineering")
                .code("CSE")
                .description("CSE Department")
                .active(true)
                .build();
        deptCse.setId(UUID.randomUUID());
    }

    private byte[] createWorkbookBytes(String[] headers, String[][] rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Departments");
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

    @Test
    @DisplayName("Normal Order: Department Name | Department Code | Description | Status")
    void testNormalOrder() throws Exception {
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());

        String[] headers = {"Department Name", "Department Code", "Description", "Status"};
        String[][] rows = {
                {"Mechanical Engineering", "MECH", "Mechanical Dept", "ACTIVE"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "dept.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        DepartmentBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertNotNull(preview);
        assertEquals(1, preview.getValidRows());
        assertEquals(0, preview.getErrorRows());
        assertEquals("Mechanical Engineering", preview.getRows().get(0).getName());
        assertEquals("MECH", preview.getRows().get(0).getCode());
        assertEquals("Mechanical Dept", preview.getRows().get(0).getDescription());
        assertTrue(preview.getRows().get(0).isActive());
    }

    @Test
    @DisplayName("Reordered Columns: Status | Department Code | Description | Department Name produces identical mapping")
    void testReorderedColumns() throws Exception {
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());

        String[] headers = {"Status", "Department Code", "Description", "Department Name"};
        String[][] rows = {
                {"ACTIVE", "MECH", "Mechanical Dept", "Mechanical Engineering"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "dept.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        DepartmentBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertNotNull(preview);
        assertEquals(1, preview.getValidRows());
        assertEquals(0, preview.getErrorRows());
        assertEquals("Mechanical Engineering", preview.getRows().get(0).getName());
        assertEquals("MECH", preview.getRows().get(0).getCode());
        assertEquals("Mechanical Dept", preview.getRows().get(0).getDescription());
        assertTrue(preview.getRows().get(0).isActive());
    }

    @Test
    @DisplayName("Header whitespace: '  Department   Name * ' is safely normalized")
    void testHeaderWhitespace() throws Exception {
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());

        String[] headers = {"  Department   Name * ", "  Department   Code * ", " Description ", " Status "};
        String[][] rows = {
                {"Civil Engineering", "CIVIL", "Civil Dept", "ACTIVE"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "dept.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        DepartmentBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertNotNull(preview);
        assertEquals(1, preview.getValidRows());
        assertEquals("Civil Engineering", preview.getRows().get(0).getName());
        assertEquals("CIVIL", preview.getRows().get(0).getCode());
    }

    @Test
    @DisplayName("Header case differences: 'department name' and 'department code' are correctly matched")
    void testHeaderCase() throws Exception {
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());

        String[] headers = {"department name", "department code", "description", "status"};
        String[][] rows = {
                {"Civil Engineering", "CIVIL", "Civil Dept", "ACTIVE"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "dept.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        DepartmentBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertNotNull(preview);
        assertEquals(1, preview.getValidRows());
        assertEquals("Civil Engineering", preview.getRows().get(0).getName());
    }

    @Test
    @DisplayName("Duplicate header: Rejects duplicate column with DUPLICATE_HEADER")
    void testDuplicateHeader() throws Exception {
        String[] headers = {"Department Name", "Department Code", "Department Name"};
        String[][] rows = {
                {"Civil Engineering", "CIVIL", "Duplicate"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "dept.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bulkUploadService.validateExcel(file));
        assertTrue(ex.getMessage().contains("Duplicate header"));
    }

    @Test
    @DisplayName("Missing required header: Rejects when required Department Code is missing")
    void testMissingRequiredHeader() throws Exception {
        String[] headers = {"Department Name", "Description", "Status"};
        String[][] rows = {
                {"Civil Engineering", "Civil Dept", "ACTIVE"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "dept.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bulkUploadService.validateExcel(file));
        assertTrue(ex.getMessage().contains("missing required column: 'Department Code *'"));
    }

    @Test
    @DisplayName("Unknown header: Rejects when header has typo 'Department Nam'")
    void testUnknownHeader() throws Exception {
        String[] headers = {"Department Nam", "Department Code"};
        String[][] rows = {
                {"Civil Engineering", "CIVIL"}
        };

        byte[] bytes = createWorkbookBytes(headers, rows);
        MockMultipartFile file = new MockMultipartFile("file", "dept.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> bulkUploadService.validateExcel(file));
        assertTrue(ex.getMessage().contains("Unknown column 'Department Nam'"));
    }

    @Test
    @DisplayName("Integration Test: Persisting new and reordered columns yields identical entity mappings")
    void testIntegrationPersistence() throws Exception {
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        // Order 1: Department Name | Department Code | Description
        String[] headersA = {"Department Name", "Department Code", "Description"};
        String[][] dataA = {
                {"Aerospace Engineering", "AERO", "Aerospace Engineering Dept"}
        };

        byte[] bytesA = createWorkbookBytes(headersA, dataA);
        MockMultipartFile fileA = new MockMultipartFile("file", "deptA.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytesA);

        DepartmentBulkUploadResponseDTO respA = bulkUploadService.bulkUpload(fileA);
        assertTrue(respA.isSuccess());
        assertEquals(1, respA.getCreatedRecords());

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository, times(1)).save(captor.capture());
        Department savedA = captor.getValue();
        assertEquals("Aerospace Engineering", savedA.getName());
        assertEquals("AERO", savedA.getCode());
        assertEquals("Aerospace Engineering Dept", savedA.getDescription());

        // Order 2: Description | Department Code | Department Name
        String[] headersB = {"Description", "Department Code", "Department Name"};
        String[][] dataB = {
                {"Aerospace Engineering Dept", "AERO", "Aerospace Engineering"}
        };

        byte[] bytesB = createWorkbookBytes(headersB, dataB);
        MockMultipartFile fileB = new MockMultipartFile("file", "deptB.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytesB);

        DepartmentBulkUploadResponseDTO respB = bulkUploadService.bulkUpload(fileB);
        assertTrue(respB.isSuccess());
        assertEquals(1, respB.getCreatedRecords());

        verify(departmentRepository, times(2)).save(captor.capture());
        Department savedB = captor.getAllValues().get(1);
        assertEquals(savedA.getName(), savedB.getName());
        assertEquals(savedA.getCode(), savedB.getCode());
        assertEquals(savedA.getDescription(), savedB.getDescription());
    }
}
