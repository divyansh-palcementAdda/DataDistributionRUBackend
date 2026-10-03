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

import com.app.datadistribution.dto.course.CourseBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.course.CourseBulkUploadResponseDTO;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.service.impl.CourseBulkUploadServiceImpl;

@ExtendWith(MockitoExtension.class)
public class CourseBulkUploadServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseTypeRepository courseTypeRepository;

    @InjectMocks
    private CourseBulkUploadServiceImpl bulkUploadService;

    private Course courseBba;
    private CourseType courseTypeUg;

    @BeforeEach
    void setUp() {
        courseTypeUg = CourseType.builder()
                .name("Undergraduate")
                .status(Status.ACTIVE)
                .build();
        courseTypeUg.setId(UUID.randomUUID());

        courseBba = Course.builder()
                .courseName("Bachelor of Business Administration (BBA)")
                .courseCode("BBA-001")
                .courseType(courseTypeUg)
                .fees(75000.0)
                .duration(3)
                .durationUnit("Years")
                .status(Status.ACTIVE)
                .build();
        courseBba.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Generate template creates valid workbook with Courses and Instructions sheets")
    void testGenerateTemplate() throws Exception {
        byte[] templateBytes = bulkUploadService.generateTemplate();
        assertNotNull(templateBytes);
        assertTrue(templateBytes.length > 0);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(templateBytes))) {
            assertEquals(2, wb.getNumberOfSheets());
            assertNotNull(wb.getSheet("Courses"));
            assertNotNull(wb.getSheet("Instructions"));

            Sheet s = wb.getSheet("Courses");
            Row headerRow = s.getRow(0);
            assertEquals("Course Name *", headerRow.getCell(0).getStringCellValue());
            assertEquals("Course Code *", headerRow.getCell(1).getStringCellValue());
            assertEquals("Course Type *", headerRow.getCell(2).getStringCellValue());
        }
    }

    @Test
    @DisplayName("Validate Excel detects new courses and existing course updates")
    void testValidateExcelSuccess() throws Exception {
        when(courseRepository.findAllByIsDeletedFalse()).thenReturn(List.of(courseBba));
        when(courseTypeRepository.findAll()).thenReturn(List.of(courseTypeUg));

        byte[] excelBytes = createSampleExcel(new String[][] {
                {"Bachelor of Business Administration (BBA)", "BBA-001", "Undergraduate", "3", "Years", "80000", "Updated desc", "ACTIVE"},
                {"Bachelor of Computer Applications (BCA)", "BCA-001", "Undergraduate", "3", "Years", "65000", "New course", "ACTIVE"}
        });

        MockMultipartFile file = new MockMultipartFile(
                "file", "courses.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        CourseBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertNotNull(preview);
        assertTrue(preview.isSuccess());
        assertEquals(2, preview.getTotalRows());
        assertEquals(2, preview.getValidRows());
        assertEquals(0, preview.getErrorRows());
        assertEquals(1, preview.getUpdateCourses());
        assertEquals(1, preview.getNewCourses());
        assertTrue(preview.isCanImport());
    }

    @Test
    @DisplayName("Validate Excel detects validation errors and in-file duplicates")
    void testValidateExcelWithErrors() throws Exception {
        when(courseRepository.findAllByIsDeletedFalse()).thenReturn(Collections.emptyList());
        when(courseTypeRepository.findAll()).thenReturn(Collections.emptyList());

        byte[] excelBytes = createSampleExcel(new String[][] {
                {"BBA", "BBA-001", "Undergraduate", "3", "Years", "75000", "", "ACTIVE"},
                {"BBA", "BBA-002", "Undergraduate", "3", "Years", "75000", "", "ACTIVE"}, // duplicate name
                {"", "BCA-001", "Undergraduate", "3", "Years", "65000", "", "ACTIVE"},    // missing name
                {"MBA", "MBA-001", "Postgraduate", "-1", "Years", "100000", "", "ACTIVE"} // invalid duration
        });

        MockMultipartFile file = new MockMultipartFile(
                "file", "courses.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        CourseBulkUploadPreviewResponseDTO preview = bulkUploadService.validateExcel(file);
        assertNotNull(preview);
        assertEquals(4, preview.getTotalRows());
        assertEquals(1, preview.getValidRows());
        assertEquals(3, preview.getErrorRows());
        assertTrue(preview.isErrorFileAvailable());

        byte[] errorFile = bulkUploadService.getErrorFile(UUID.fromString(preview.getImportId()));
        assertNotNull(errorFile);
        assertTrue(errorFile.length > 0);
    }

    @Test
    @DisplayName("Bulk upload executes creation and updates successfully")
    void testBulkUploadExecute() throws Exception {
        when(courseRepository.findAllByIsDeletedFalse()).thenReturn(List.of(courseBba));
        when(courseTypeRepository.findAll()).thenReturn(List.of(courseTypeUg));
        when(courseRepository.findById(courseBba.getId())).thenReturn(Optional.of(courseBba));

        byte[] excelBytes = createSampleExcel(new String[][] {
                {"Bachelor of Business Administration (BBA)", "BBA-001", "Undergraduate", "3", "Years", "80000", "Updated desc", "ACTIVE"},
                {"New Master Course", "NMC-001", "Postgraduate", "2", "Years", "110000", "New course", "ACTIVE"}
        });

        MockMultipartFile file = new MockMultipartFile(
                "file", "courses.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        CourseBulkUploadResponseDTO response = bulkUploadService.bulkUpload(file);
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(2, response.getTotalRows());
        assertEquals(2, response.getSuccessfulRows());
        assertEquals(1, response.getCreatedRecords());
        assertEquals(1, response.getUpdatedRecords());

        verify(courseRepository, atLeastOnce()).save(any(Course.class));
    }

    @Test
    @DisplayName("Get error file throws exception if import ID not in cache")
    void testGetErrorFileNotFound() {
        assertThrows(ResourcesNotFoundException.class, () -> bulkUploadService.getErrorFile(UUID.randomUUID()));
    }

    private byte[] createSampleExcel(String[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Courses");
            Row hRow = sheet.createRow(0);
            String[] headers = {"Course Name", "Course Code", "Course Type", "Duration", "Duration Unit", "Fees", "Description", "Status"};
            for (int i = 0; i < headers.length; i++) {
                hRow.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
