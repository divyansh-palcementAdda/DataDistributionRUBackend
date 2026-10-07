package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.app.datadistribution.dto.department.DepartmentBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.department.DepartmentBulkUploadResponseDTO;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.service.impl.DepartmentBulkUploadServiceImpl;

@ExtendWith(MockitoExtension.class)
public class DepartmentBulkUploadServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentBulkUploadServiceImpl bulkUploadService;

    private Department deptCse;
    private Department deptSom;

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
    }

    @Test
    @DisplayName("Generate template creates valid workbook with Departments and Instructions sheets")
    void testGenerateTemplate() throws Exception {
        byte[] templateBytes = bulkUploadService.generateTemplate();
        assertNotNull(templateBytes);
        assertTrue(templateBytes.length > 0);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(templateBytes))) {
            assertEquals(2, wb.getNumberOfSheets());
            assertNotNull(wb.getSheet("Departments"));
            assertNotNull(wb.getSheet("Instructions"));

            Sheet s = wb.getSheet("Departments");
            Row headerRow = s.getRow(0);
            assertEquals("S.No.", headerRow.getCell(0).getStringCellValue());
            assertEquals("Department Name *", headerRow.getCell(1).getStringCellValue());
            assertEquals("Department Code *", headerRow.getCell(2).getStringCellValue());
            assertEquals("Description", headerRow.getCell(3).getStringCellValue());
            assertEquals("Status", headerRow.getCell(4).getStringCellValue());
        }
    }

    @Test
    @DisplayName("Validate Excel detects new departments and existing updates")
    void testValidateExcel_NewAndExisting() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCse));

        byte[] excelBytes = createExcel(List.of(
                new String[]{"1", "Computer Science & Engineering", "CSE", "Updated CSE Description", "ACTIVE"},
                new String[]{"2", "Information Technology", "IT", "New IT Department", "ACTIVE"}
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "departments.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        DepartmentBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertTrue(preview.isSuccess());
        assertEquals(2, preview.getTotalRows());
        assertEquals(2, preview.getValidRows());
        assertEquals(0, preview.getErrorRows());
        assertEquals(1, preview.getNewDepartments());
        assertEquals(1, preview.getUpdateDepartments());
        assertTrue(preview.isCanImport());
    }

    @Test
    @DisplayName("Validate Excel flags duplicate names within same Excel file")
    void testValidateExcel_DuplicateInSheet() throws Exception {
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());

        byte[] excelBytes = createExcel(List.of(
                new String[]{"1", "Biotechnology", "BT", "Bio Tech", "ACTIVE"},
                new String[]{"2", "Biotechnology", "BIO", "Duplicate Bio Tech", "ACTIVE"}
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "departments.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        DepartmentBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertEquals(2, preview.getTotalRows());
        assertEquals(1, preview.getValidRows());
        assertEquals(1, preview.getErrorRows());
        assertEquals("DUPLICATE_NAME_IN_SHEET", preview.getErrors().get(0).getErrorCode());
        assertTrue(preview.isErrorFileAvailable());
    }

    @Test
    @DisplayName("Validate Excel flags missing required fields")
    void testValidateExcel_MissingRequiredFields() throws Exception {
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());

        byte[] excelBytes = createExcel(List.of(
                new String[]{"1", "", "MECH", "Mechanical", "ACTIVE"},
                new String[]{"2", "Civil Engineering", "", "Civil", "ACTIVE"}
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "departments.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        DepartmentBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertEquals(2, preview.getTotalRows());
        assertEquals(0, preview.getValidRows());
        assertEquals(2, preview.getErrorRows());
        assertEquals("MISSING_NAME", preview.getErrors().get(0).getErrorCode());
        assertEquals("MISSING_CODE", preview.getErrors().get(1).getErrorCode());
    }

    @Test
    @DisplayName("Validate Excel flags collision when name matches one dept and code matches another")
    void testValidateExcel_Conflict() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCse, deptSom));

        byte[] excelBytes = createExcel(Collections.singletonList(
                new String[]{"1", "Computer Science & Engineering", "SOM", "Conflict row", "ACTIVE"}
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "departments.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        DepartmentBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertEquals(1, preview.getErrorRows());
        assertEquals("DEPARTMENT_CONFLICT", preview.getErrors().get(0).getErrorCode());
    }

    @Test
    @DisplayName("Bulk upload successfully persists new and updated departments")
    void testBulkUpload_Persistence() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(deptCse));
        when(departmentRepository.findById(deptCse.getId())).thenReturn(Optional.of(deptCse));
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] excelBytes = createExcel(List.of(
                new String[]{"1", "Computer Science & Engineering", "CSE", "Updated Description", "ACTIVE"},
                new String[]{"2", "Electrical Engineering", "EE", "EE Dept", "ACTIVE"}
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "departments.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        DepartmentBulkUploadResponseDTO response = bulkUploadService.bulkUpload(file);
        assertTrue(response.isSuccess());
        assertEquals(2, response.getSuccessfulRows());
        assertEquals(1, response.getCreatedRecords());
        assertEquals(1, response.getUpdatedRecords());
        verify(departmentRepository, atLeastOnce()).save(any(Department.class));
    }

    @Test
    @DisplayName("Error file download works for validated imports with issues")
    void testErrorFileDownload() throws Exception {
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());

        byte[] excelBytes = createExcel(Collections.singletonList(
                new String[]{"1", "", "", "", "ACTIVE"}
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "departments.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        DepartmentBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertTrue(preview.isErrorFileAvailable());
        assertNotNull(preview.getImportId());

        byte[] errorFile = bulkUploadService.getErrorFile(UUID.fromString(preview.getImportId()));
        assertNotNull(errorFile);
        assertTrue(errorFile.length > 0);

        // Unknown import ID throws ResourcesNotFoundException
        assertThrows(ResourcesNotFoundException.class, () -> bulkUploadService.getErrorFile(UUID.randomUUID()));
    }

    private byte[] createExcel(List<String[]> dataRows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Departments");
            Row hRow = sheet.createRow(0);
            String[] headers = DepartmentBulkUploadServiceImpl.TEMPLATE_HEADERS;
            for (int i = 0; i < headers.length; i++) {
                hRow.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < dataRows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                String[] d = dataRows.get(r);
                for (int c = 0; c < d.length; c++) {
                    row.createCell(c).setCellValue(d[c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
