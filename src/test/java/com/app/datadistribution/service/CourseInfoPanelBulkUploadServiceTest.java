package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.infopanel.CourseInfoPanelBulkUploadResponseDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelPreviewResponseDTO;
import com.app.datadistribution.entity.CompetitorCourseComparison;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseInfoPanel;
import com.app.datadistribution.entity.CourseInfoPanelCompetitor;
import com.app.datadistribution.entity.CourseInfoPanelCompetitorBranch;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.repository.CompetitorCourseComparisonRepository;
import com.app.datadistribution.repository.CourseInfoPanelCompetitorBranchRepository;
import com.app.datadistribution.repository.CourseInfoPanelCompetitorRepository;
import com.app.datadistribution.repository.CourseInfoPanelRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.service.impl.CourseInfoPanelBulkUploadServiceImpl;

@ExtendWith(MockitoExtension.class)
public class CourseInfoPanelBulkUploadServiceTest {

        @Mock
        private CourseRepository courseRepository;

        @Mock
        private CourseInfoPanelRepository infoPanelRepository;

        @Mock
        private CourseInfoPanelCompetitorRepository competitorRepository;

        @Mock
        private CourseInfoPanelCompetitorBranchRepository branchRepository;

        @Mock
        private CompetitorCourseComparisonRepository comparisonRepository;

        @InjectMocks
        private CourseInfoPanelBulkUploadServiceImpl bulkUploadService;

        private Course courseBba;
        private Course courseBca;

        @BeforeEach
        void setUp() {
                courseBba = Course.builder()
                                .courseName("BBA")
                                .courseCode("BBA01")
                                .fees(75000.0)
                                .duration(3)
                                .durationUnit("Years")
                                .build();
                courseBba.setId(UUID.randomUUID());

                courseBca = Course.builder()
                                .courseName("BCA")
                                .courseCode("BCA01")
                                .fees(65000.0)
                                .duration(3)
                                .durationUnit("Years")
                                .build();
                courseBca.setId(UUID.randomUUID());

                // Set admin authentication context
                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken("admin", "pass",
                                                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        }

        @Test
        @DisplayName("Generate template generates valid workbook with 3 sheets and required headers")
        void testGenerateTemplate() throws Exception {
                byte[] templateBytes = bulkUploadService.generateTemplate();
                assertNotNull(templateBytes);
                assertTrue(templateBytes.length > 0);

                try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(templateBytes))) {
                        assertEquals(3, wb.getNumberOfSheets());
                        assertNotNull(wb.getSheet("Course Info"));
                        assertNotNull(wb.getSheet("Other Colleges"));
                        assertNotNull(wb.getSheet("Instructions"));

                        Sheet s1 = wb.getSheet("Course Info");
                        Row r0 = s1.getRow(0);
                        assertEquals("Course Name *", r0.getCell(0).getStringCellValue());
                        assertEquals("Academic Session *", r0.getCell(1).getStringCellValue());

                        Sheet s2 = wb.getSheet("Other Colleges");
                        Row s2R0 = s2.getRow(0);
                        assertEquals("Course Name *", s2R0.getCell(0).getStringCellValue());
                        assertEquals("Academic Session *", s2R0.getCell(1).getStringCellValue());
                        assertEquals("College Name *", s2R0.getCell(2).getStringCellValue());
                }
        }

        @Test
        @DisplayName("Validate Excel detects valid rows, new records, and updates correctly")
        void testValidateExcelSuccess() throws Exception {
                when(courseRepository.findAllByIsDeletedFalse()).thenReturn(List.of(courseBba, courseBca));
                when(infoPanelRepository.existsByCourseIdAndAcademicSessionAndIsDeletedFalse(eq(courseBba.getId()),
                                eq("2026-27"))).thenReturn(false);
                when(infoPanelRepository.existsByCourseIdAndAcademicSessionAndIsDeletedFalse(eq(courseBca.getId()),
                                eq("2026-27"))).thenReturn(true);

                byte[] excelBytes = createSampleExcel(
                                new String[][] {
                                                { "BBA", "2026-27", "School of Management", "₹ 75,000", "3 Years" },
                                                { "BCA", "2026-27", "School of CS", "₹ 65,000", "3 Years" }
                                },
                                new String[][] {
                                                { "BBA", "2026-27", "Prestige Institute", "Main Campus", "₹ 1,00,000" },
                                                { "BBA", "2026-27", "Acropolis Institute", "Bypass Campus", "₹ 80,000" }
                                });

                MockMultipartFile file = new MockMultipartFile(
                                "file", "course_info.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

                CourseInfoPanelPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

                assertNotNull(preview);
                assertTrue(preview.isSuccess());
                assertEquals(4, preview.getTotalRows());
                assertEquals(4, preview.getValidRows());
                assertEquals(0, preview.getErrorRows());
                assertEquals(1, preview.getNewRecords());
                assertEquals(1, preview.getRecordsToUpdate());
                assertTrue(preview.isCanImport());
                assertFalse(preview.isErrorFileAvailable());
        }

        @Test
        @DisplayName("Validate Excel detects duplicate row errors while auto-creating unknown courses")
        void testValidateExcelWithErrors() throws Exception {
                when(courseRepository.findAllByIsDeletedFalse()).thenReturn(List.of(courseBba));

                byte[] excelBytes = createSampleExcel(
                                new String[][] {
                                                { "BBA", "2026-27", "School of Management" },
                                                { "BBA", "2026-27", "Duplicate Row" },
                                                { "UNKNOWN_COURSE_XYZ", "2026-27", "Unknown" }
                                },
                                new String[][] {
                                                { "BBA", "2026-27", "College A", "Branch 1" },
                                                { "BBA", "2026-27", "College A", "Branch 1" } // duplicate competitor + branch
                                });

                MockMultipartFile file = new MockMultipartFile(
                                "file", "course_info.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

                CourseInfoPanelPreviewResponseDTO preview = bulkUploadService.validateExcel(file);

                assertNotNull(preview);
                assertEquals(5, preview.getTotalRows());
                assertEquals(2, preview.getErrorRows()); // 1 duplicate s1, 1 duplicate s2 (UNKNOWN_COURSE_XYZ is auto-created!)
                assertEquals(3, preview.getValidRows());
                assertTrue(preview.isErrorFileAvailable());
                assertNotNull(preview.getImportId());

                // Error sheet can be downloaded
                byte[] errorFile = bulkUploadService.getErrorFile(UUID.fromString(preview.getImportId()));
                assertNotNull(errorFile);
                assertTrue(errorFile.length > 0);
        }

        @Test
        @DisplayName("Bulk upload auto-creates missing course during import execution")
        void testBulkUploadAutoCreatesMissingCourse() throws Exception {
                when(courseRepository.findAllByIsDeletedFalse()).thenReturn(Collections.emptyList());
                when(courseRepository.existsByCourseCodeIgnoreCase(any())).thenReturn(false);

                Course savedCourse = Course.builder()
                                .courseName("DATA_SCIENCE_NEW")
                                .courseCode("DATASCIENCENEW")
                                .duration(3)
                                .durationUnit("Years")
                                .fees(80000.0)
                                .build();
                savedCourse.setId(UUID.randomUUID());
                when(courseRepository.save(any(Course.class))).thenReturn(savedCourse);

                CourseInfoPanel mockSavedPanel = CourseInfoPanel.builder()
                                .course(savedCourse)
                                .academicSession("2026-27")
                                .courseName("DATA_SCIENCE_NEW")
                                .build();
                mockSavedPanel.setId(UUID.randomUUID());
                when(infoPanelRepository.save(any(CourseInfoPanel.class))).thenReturn(mockSavedPanel);

                byte[] excelBytes = createSampleExcel(
                                new String[][] {
                                                { "DATA_SCIENCE_NEW", "2026-27", "School of Data", "₹ 80,000", "3 Years" }
                                },
                                new String[][] {});

                MockMultipartFile file = new MockMultipartFile(
                                "file", "course_info.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

                CourseInfoPanelBulkUploadResponseDTO response = bulkUploadService.bulkUpload(file);
                assertNotNull(response);
                assertTrue(response.isSuccess());
                assertEquals(1, response.getSuccessfulRows());
                verify(courseRepository, atLeastOnce()).save(any(Course.class));
                verify(infoPanelRepository, atLeastOnce()).save(any(CourseInfoPanel.class));
        }

        @Test
        @DisplayName("Bulk upload creates new Info Panels and competitor branches")
        void testBulkUploadCreateAndUpsert() throws Exception {
                when(courseRepository.findAllByIsDeletedFalse()).thenReturn(List.of(courseBba));
                when(infoPanelRepository.findByCourseIdAndAcademicSessionAndIsDeletedFalse(eq(courseBba.getId()),
                                eq("2026-27")))
                                .thenReturn(Optional.empty());

                CourseInfoPanel mockSavedPanel = CourseInfoPanel.builder()
                                .course(courseBba)
                                .academicSession("2026-27")
                                .courseName("BBA")
                                .build();
                mockSavedPanel.setId(UUID.randomUUID());

                when(infoPanelRepository.save(any(CourseInfoPanel.class))).thenReturn(mockSavedPanel);

                CourseInfoPanelCompetitor mockCompetitor = CourseInfoPanelCompetitor.builder()
                                .infoPanel(mockSavedPanel)
                                .collegeName("Prestige Institute")
                                .build();
                mockCompetitor.setId(UUID.randomUUID());

                when(competitorRepository.findByInfoPanelIdAndCollegeNameIgnoreCaseAndIsDeletedFalse(
                                eq(mockSavedPanel.getId()), eq("Prestige Institute")))
                                .thenReturn(Optional.empty());
                when(competitorRepository.save(any(CourseInfoPanelCompetitor.class))).thenReturn(mockCompetitor);
                when(comparisonRepository.findByCompetitorIdAndIsDeletedFalse(mockCompetitor.getId()))
                                .thenReturn(Optional.empty());
                when(branchRepository.existsByCompetitorIdAndBranchNameIgnoreCaseAndIsDeletedFalse(
                                mockCompetitor.getId(), "Main Campus")).thenReturn(false);

                byte[] excelBytes = createSampleExcel(
                                new String[][] {
                                                { "BBA", "2026-27", "School of Management", "₹ 75,000", "3 Years",
                                                                "10+2", "Manager", "₹ 60,000" }
                                },
                                new String[][] {
                                                { "BBA", "2026-27", "Prestige Institute", "Main Campus", "₹ 1,10,000",
                                                                "3 Years", "Strict", "10+2" }
                                });

                MockMultipartFile file = new MockMultipartFile(
                                "file", "course_info.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

                CourseInfoPanelBulkUploadResponseDTO response = bulkUploadService.bulkUpload(file);

                assertNotNull(response);
                assertTrue(response.isSuccess());
                assertEquals(2, response.getTotalRows());
                assertEquals(2, response.getSuccessfulRows());
                assertEquals(0, response.getFailedRows());
                assertEquals(1, response.getCreatedRecords());

                verify(infoPanelRepository, atLeastOnce()).save(any(CourseInfoPanel.class));
                verify(competitorRepository, atLeastOnce()).save(any(CourseInfoPanelCompetitor.class));
                verify(comparisonRepository, atLeastOnce()).save(any(CompetitorCourseComparison.class));
                verify(branchRepository, atLeastOnce()).save(any(CourseInfoPanelCompetitorBranch.class));
        }

        @Test
        @DisplayName("Get error file throws exception if import ID is unknown")
        void testGetErrorFileNotFound() {
                assertThrows(ResourcesNotFoundException.class, () -> bulkUploadService.getErrorFile(UUID.randomUUID()));
        }

        // Helper to generate in-memory mock Excel file
        private byte[] createSampleExcel(String[][] sheet1Rows, String[][] sheet2Rows) throws Exception {
                try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        Sheet s1 = wb.createSheet("Course Info");
                        Row s1H = s1.createRow(0);
                        String[] s1Cols = { "Course Name", "Academic Session", "School", "Course Fee", "Duration",
                                        "Eligibility", "Job Opportunities", "Hostel Fee" };
                        for (int i = 0; i < s1Cols.length; i++)
                                s1H.createCell(i).setCellValue(s1Cols[i]);

                        for (int r = 0; r < sheet1Rows.length; r++) {
                                Row row = s1.createRow(r + 1);
                                String[] vals = sheet1Rows[r];
                                for (int c = 0; c < vals.length; c++) {
                                        row.createCell(c).setCellValue(vals[c]);
                                }
                        }

                        Sheet s2 = wb.createSheet("Other Colleges");
                        Row s2H = s2.createRow(0);
                        String[] s2Cols = { "Course Name", "Academic Session", "College Name", "Branch",
                                        "Course Fee Per Year", "Duration", "Odds", "Eligibility" };
                        for (int i = 0; i < s2Cols.length; i++)
                                s2H.createCell(i).setCellValue(s2Cols[i]);

                        for (int r = 0; r < sheet2Rows.length; r++) {
                                Row row = s2.createRow(r + 1);
                                String[] vals = sheet2Rows[r];
                                for (int c = 0; c < vals.length; c++) {
                                        row.createCell(c).setCellValue(vals[c]);
                                }
                        }

                        wb.write(out);
                        return out.toByteArray();
                }
        }
}
