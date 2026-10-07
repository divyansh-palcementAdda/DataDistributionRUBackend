package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.app.datadistribution.dto.lead.BulkLeadUploadResponse;
import com.app.datadistribution.entity.Board;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.entity.Grade;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.entity.Stream;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.BoardRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.GradeRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.LeadStatusHistoryRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.ProgramRepository;
import com.app.datadistribution.repository.StreamRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.LeadAcademicResolutionResult;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.impl.LeadBulkUploadServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.util.LeadAcademicResolver;
import com.app.datadistribution.service.util.ProgramCourseResolver;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class LeadBulkUploadServiceImplTest {

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
        private StreamRepository streamRepository;
        @Mock
        private CourseRepository courseRepository;
        @Mock
        private ProgramRepository programRepository;
        @Mock
        private ProgramCourseResolver programCourseResolver;
        @Mock
        private LeadAcademicResolver leadAcademicResolver;
        @Mock
        private CourseTypeRepository courseTypeRepository;
        @Mock
        private LeadStatusHistoryRepository leadStatusHistoryRepository;
        @Mock
        private ILeadDataScopeService leadDataScopeService;

        @InjectMocks
        private LeadBulkUploadServiceImpl bulkUploadService;

        private User currentUser;
        private LeadStatus defaultStatus;

        @BeforeEach
        void setUp() throws UnauthorizedException, BadRequestException {
                SecurityContext securityContext = mock(SecurityContext.class);
                Authentication authentication = mock(Authentication.class);
                lenient().when(authentication.isAuthenticated()).thenReturn(true);
                lenient().when(authentication.getName()).thenReturn("admin");
                lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
                SecurityContextHolder.setContext(securityContext);

                currentUser = User.builder().username("admin").active(true).build();
                currentUser.setId(UUID.randomUUID());
                lenient().when(userRepository.findByUsername("admin")).thenReturn(Optional.of(currentUser));

                UserDataScope dataScope = UserDataScope.builder()
                                .scopeType(UserDataScope.ScopeType.SYSTEM)
                                .userId(currentUser.getId())
                                .build();
                lenient().when(leadDataScopeService.getCurrentUserScope()).thenReturn(dataScope);

                defaultStatus = LeadStatus.builder().name("Raw").code("RAW").active(true).build();
                lenient().when(leadStatusRepository.findByCodeIgnoreCase("RAW")).thenReturn(Optional.of(defaultStatus));
                lenient().when(leadRepository.findAllActivePhoneNumbers()).thenReturn(Collections.emptyList());

                lenient().when(leadRepository.save(any())).thenAnswer(invocation -> {
                        Lead l = invocation.getArgument(0);
                        if (l.getId() == null) {
                                l.setId(UUID.randomUUID());
                        }
                        return l;
                });
                lenient().when(leadRepository.findById(any())).thenReturn(Optional.empty());
        }

        @Test
        void testDownloadTemplate_ReturnsValidBytes() {
                byte[] templateBytes = bulkUploadService.downloadTemplate();
                assertNotNull(templateBytes);
                assertTrue(templateBytes.length > 0);
        }

        @Test
        void testBulkUpload_Success() throws Exception {
                when(leadAcademicResolver.resolveLeadAcademicMappingsForUpload(any(), any(), any(), any(), any(), any(),
                                any(), anyInt()))
                                .thenReturn(LeadAcademicResolutionResult.builder()
                                                .programs(Collections.emptySet())
                                                .interestedCourses(Collections.emptySet())
                                                .build());

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (Workbook workbook = new XSSFWorkbook()) {
                        Sheet sheet = workbook.createSheet("Leads");
                        Row header = sheet.createRow(0);
                        header.createCell(0).setCellValue("Full Name");
                        header.createCell(1).setCellValue("Phone Number");
                        header.createCell(2).setCellValue("Email");

                        Row row1 = sheet.createRow(1);
                        row1.createCell(0).setCellValue("Alice Brown");
                        row1.createCell(1).setCellValue("+919988776655");
                        row1.createCell(2).setCellValue("alice@example.com");

                        workbook.write(out);
                }

                MockMultipartFile file = new MockMultipartFile(
                                "file", "leads.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                out.toByteArray());

                BulkLeadUploadResponse response = bulkUploadService.bulkUploadLeads(
                                file, null, null, null, null, null, null, null, null, null, null, null);

                assertNotNull(response);
                assertEquals(1, response.getTotalRows());
                assertEquals(1, response.getSuccessCount());
                assertEquals(0, response.getFailedCount());
                assertEquals(0, response.getDuplicateCount());
        }

        /**
         * Requirement 32: Realistic Data Fixture Test
         */
        @Test
        void testBulkUpload_RealisticData_CourseAndProgramPersistence() throws Exception {
                UUID mbaId = UUID.randomUUID();
                Course mbaCourse = Course.builder().courseName("MBA").courseCode("MBA").build();
                mbaCourse.setId(mbaId);

                UUID somId = UUID.randomUUID();
                Program somProgram = Program.builder().name("School of Management").code("SOM").build();
                somProgram.setId(somId);

                UUID ugId = UUID.randomUUID();
                CourseType ugType = CourseType.builder().name("Undergraduate").build();
                ugType.setId(ugId);

                UUID streamId = UUID.randomUUID();
                Stream commerceStream = Stream.builder().name("Commerce").code("COMM").active(true).build();
                commerceStream.setId(streamId);

                UUID gradeId = UUID.randomUUID();
                Grade aGrade = Grade.builder().name("A Grade").code("A_GRADE").active(true).build();
                aGrade.setId(gradeId);

                UUID sourceId = UUID.randomUUID();
                LeadSource oraiSource = LeadSource.builder().name("Orai").active(true).build();
                oraiSource.setId(sourceId);

                when(leadAcademicResolver.resolveLeadAcademicMappingsForUpload(eq("MBA"), isNull(),
                                eq("School of Management"), isNull(), eq("Undergraduate"), isNull(), any(), anyInt()))
                                .thenReturn(LeadAcademicResolutionResult.builder()
                                                .course(mbaCourse)
                                                .program(somProgram)
                                                .programs(Set.of(somProgram))
                                                .courseType(ugType)
                                                .interestedCourses(Set.of(mbaCourse))
                                                .courseSource("EXCEL")
                                                .programSource("EXCEL")
                                                .courseTypeSource("EXCEL")
                                                .build());

                when(streamRepository.findByNameIgnoreCaseAndIsDeletedFalse("Commerce"))
                                .thenReturn(Optional.of(commerceStream));
                when(gradeRepository.findByNameIgnoreCaseAndIsDeletedFalse("A Grade")).thenReturn(Optional.of(aGrade));
                when(leadSourceRepository.findByNameIgnoreCaseAndIsDeletedFalse("Orai"))
                                .thenReturn(Optional.of(oraiSource));

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (Workbook workbook = new XSSFWorkbook()) {
                        Sheet sheet = workbook.createSheet("Leads");
                        Row header = sheet.createRow(0);
                        header.createCell(0).setCellValue("Full Name");
                        header.createCell(1).setCellValue("Phone Number");
                        header.createCell(2).setCellValue("Program");
                        header.createCell(3).setCellValue("Course Interested");
                        header.createCell(4).setCellValue("Course Type");
                        header.createCell(5).setCellValue("Lead Source");
                        header.createCell(6).setCellValue("Grade");
                        header.createCell(7).setCellValue("Stream");

                        Row row1 = sheet.createRow(1);
                        row1.createCell(0).setCellValue("Test Student");
                        row1.createCell(1).setCellValue("+919123456789");
                        row1.createCell(2).setCellValue("School of Management");
                        row1.createCell(3).setCellValue("MBA");
                        row1.createCell(4).setCellValue("Undergraduate");
                        row1.createCell(5).setCellValue("Orai");
                        row1.createCell(6).setCellValue("A Grade");
                        row1.createCell(7).setCellValue("Commerce");

                        workbook.write(out);
                }

                MockMultipartFile file = new MockMultipartFile(
                                "file", "leads.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                out.toByteArray());

                ArgumentCaptor<Lead> leadCaptor = ArgumentCaptor.forClass(Lead.class);

                BulkLeadUploadResponse response = bulkUploadService.bulkUploadLeads(
                                file, null, null, null, null, null, null, null, null, null, null, null);

                assertEquals(1, response.getTotalRows());
                assertEquals(1, response.getSuccessCount());
                assertEquals(0, response.getFailedCount());

                verify(leadRepository).save(leadCaptor.capture());
                Lead captured = leadCaptor.getValue();
                assertEquals("Test Student", captured.getFullName());
                assertNotNull(captured.getCourse());
                assertEquals(mbaId, captured.getCourse().getId());
                assertEquals(1, captured.getPrograms().size());
                assertEquals(somId, captured.getPrograms().iterator().next().getId());
                assertNotNull(captured.getCourseType());
                assertEquals(ugId, captured.getCourseType().getId());
                assertEquals(1, captured.getLeadSources().size());
                assertEquals("Orai", captured.getLeadSources().iterator().next().getName());
                assertEquals("A Grade", captured.getGrade().getName());
                assertEquals("Commerce", captured.getStream().getName());
        }

        /**
         * Requirement 33: Course-only auto mapping Program
         */
        @Test
        void testBulkUpload_CourseOnly_AutoMappingProgram() throws Exception {
                UUID mbaId = UUID.randomUUID();
                Course mbaCourse = Course.builder().courseName("MBA").build();
                mbaCourse.setId(mbaId);

                UUID somId = UUID.randomUUID();
                Program somProgram = Program.builder().name("School of Management").build();
                somProgram.setId(somId);

                when(leadAcademicResolver.resolveLeadAcademicMappingsForUpload(eq("MBA"), isNull(), isNull(), isNull(),
                                isNull(), isNull(), any(), anyInt()))
                                .thenReturn(LeadAcademicResolutionResult.builder()
                                                .course(mbaCourse)
                                                .program(somProgram)
                                                .programs(Set.of(somProgram))
                                                .interestedCourses(Set.of(mbaCourse))
                                                .courseSource("EXCEL")
                                                .programSource("COURSE")
                                                .build());

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (Workbook workbook = new XSSFWorkbook()) {
                        Sheet sheet = workbook.createSheet("Leads");
                        Row header = sheet.createRow(0);
                        header.createCell(0).setCellValue("Full Name");
                        header.createCell(1).setCellValue("Phone Number");
                        header.createCell(2).setCellValue("Course Interested");

                        Row row1 = sheet.createRow(1);
                        row1.createCell(0).setCellValue("Student A");
                        row1.createCell(1).setCellValue("+919111111111");
                        row1.createCell(2).setCellValue("MBA");

                        workbook.write(out);
                }

                MockMultipartFile file = new MockMultipartFile(
                                "file", "leads.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                out.toByteArray());

                ArgumentCaptor<Lead> leadCaptor = ArgumentCaptor.forClass(Lead.class);

                BulkLeadUploadResponse response = bulkUploadService.bulkUploadLeads(
                                file, null, null, null, null, null, null, null, null, null, null, null);

                assertEquals(1, response.getSuccessCount());
                verify(leadRepository).save(leadCaptor.capture());
                Lead saved = leadCaptor.getValue();
                assertEquals(mbaId, saved.getCourse().getId());
                assertEquals(1, saved.getPrograms().size());
                assertEquals(somId, saved.getPrograms().iterator().next().getId());
        }

        /**
         * Requirement 34: Program-only (Course = blank, CourseType = blank)
         */
        @Test
        void testBulkUpload_ProgramOnly() throws Exception {
                UUID somId = UUID.randomUUID();
                Program somProgram = Program.builder().name("School of Management").build();
                somProgram.setId(somId);

                when(leadAcademicResolver.resolveLeadAcademicMappingsForUpload(isNull(), isNull(),
                                eq("School of Management"), isNull(), isNull(), isNull(), any(), anyInt()))
                                .thenReturn(LeadAcademicResolutionResult.builder()
                                                .program(somProgram)
                                                .programs(Set.of(somProgram))
                                                .course(null)
                                                .courseType(null)
                                                .programSource("EXCEL")
                                                .build());

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (Workbook workbook = new XSSFWorkbook()) {
                        Sheet sheet = workbook.createSheet("Leads");
                        Row header = sheet.createRow(0);
                        header.createCell(0).setCellValue("Full Name");
                        header.createCell(1).setCellValue("Phone Number");
                        header.createCell(2).setCellValue("Program");

                        Row row1 = sheet.createRow(1);
                        row1.createCell(0).setCellValue("Student B");
                        row1.createCell(1).setCellValue("+919222222222");
                        row1.createCell(2).setCellValue("School of Management");

                        workbook.write(out);
                }

                MockMultipartFile file = new MockMultipartFile(
                                "file", "leads.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                out.toByteArray());

                ArgumentCaptor<Lead> leadCaptor = ArgumentCaptor.forClass(Lead.class);

                BulkLeadUploadResponse response = bulkUploadService.bulkUploadLeads(
                                file, null, null, null, null, null, null, null, null, null, null, null);

                assertEquals(1, response.getSuccessCount());
                verify(leadRepository).save(leadCaptor.capture());
                Lead saved = leadCaptor.getValue();
                assertNull(saved.getCourse());
                assertNull(saved.getCourseType());
                assertEquals(1, saved.getPrograms().size());
        }

        /**
         * Requirement 35: Course Type Only (Course = blank, Program = blank)
         */
        @Test
        void testBulkUpload_CourseTypeOnly() throws Exception {
                UUID ctId = UUID.randomUUID();
                CourseType ct = CourseType.builder().name("Undergraduate").build();
                ct.setId(ctId);

                when(leadAcademicResolver.resolveLeadAcademicMappingsForUpload(isNull(), isNull(), isNull(), isNull(),
                                eq("Undergraduate"), isNull(), any(), anyInt()))
                                .thenReturn(LeadAcademicResolutionResult.builder()
                                                .course(null)
                                                .courseType(ct)
                                                .programs(Collections.emptySet())
                                                .courseTypeSource("EXCEL")
                                                .build());

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (Workbook workbook = new XSSFWorkbook()) {
                        Sheet sheet = workbook.createSheet("Leads");
                        Row header = sheet.createRow(0);
                        header.createCell(0).setCellValue("Full Name");
                        header.createCell(1).setCellValue("Phone Number");
                        header.createCell(2).setCellValue("Course Type");

                        Row row1 = sheet.createRow(1);
                        row1.createCell(0).setCellValue("Student C");
                        row1.createCell(1).setCellValue("+919333333333");
                        row1.createCell(2).setCellValue("Undergraduate");

                        workbook.write(out);
                }

                MockMultipartFile file = new MockMultipartFile(
                                "file", "leads.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                out.toByteArray());

                ArgumentCaptor<Lead> leadCaptor = ArgumentCaptor.forClass(Lead.class);

                BulkLeadUploadResponse response = bulkUploadService.bulkUploadLeads(
                                file, null, null, null, null, null, null, null, null, null, null, null);

                assertEquals(1, response.getSuccessCount());
                verify(leadRepository).save(leadCaptor.capture());
                Lead saved = leadCaptor.getValue();
                assertNull(saved.getCourse());
                assertTrue(saved.getPrograms().isEmpty());
                assertNotNull(saved.getCourseType());
                assertEquals(ctId, saved.getCourseType().getId());
        }

        /**
         * Requirement 36: Course + Program Preserved
         */
        @Test
        void testBulkUpload_CourseAndProgram_Preserved() throws Exception {
                UUID mbaId = UUID.randomUUID();
                Course mbaCourse = Course.builder().courseName("MBA").build();
                mbaCourse.setId(mbaId);

                UUID somId = UUID.randomUUID();
                Program somProgram = Program.builder().name("School of Management").build();
                somProgram.setId(somId);

                when(leadAcademicResolver.resolveLeadAcademicMappingsForUpload(eq("MBA"), isNull(),
                                eq("School of Management"), isNull(), isNull(), isNull(), any(), anyInt()))
                                .thenReturn(LeadAcademicResolutionResult.builder()
                                                .course(mbaCourse)
                                                .program(somProgram)
                                                .programs(Set.of(somProgram))
                                                .interestedCourses(Set.of(mbaCourse))
                                                .courseSource("EXCEL")
                                                .programSource("EXCEL")
                                                .build());

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (Workbook workbook = new XSSFWorkbook()) {
                        Sheet sheet = workbook.createSheet("Leads");
                        Row header = sheet.createRow(0);
                        header.createCell(0).setCellValue("Full Name");
                        header.createCell(1).setCellValue("Phone Number");
                        header.createCell(2).setCellValue("Course Interested");
                        header.createCell(3).setCellValue("Program");

                        Row row1 = sheet.createRow(1);
                        row1.createCell(0).setCellValue("Student D");
                        row1.createCell(1).setCellValue("+919444444444");
                        row1.createCell(2).setCellValue("MBA");
                        row1.createCell(3).setCellValue("School of Management");

                        workbook.write(out);
                }

                MockMultipartFile file = new MockMultipartFile(
                                "file", "leads.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                out.toByteArray());

                ArgumentCaptor<Lead> leadCaptor = ArgumentCaptor.forClass(Lead.class);

                BulkLeadUploadResponse response = bulkUploadService.bulkUploadLeads(
                                file, null, null, null, null, null, null, null, null, null, null, null);

                assertEquals(1, response.getSuccessCount());
                verify(leadRepository).save(leadCaptor.capture());
                Lead saved = leadCaptor.getValue();
                assertEquals(mbaId, saved.getCourse().getId());
                assertEquals(1, saved.getPrograms().size());
                assertEquals(somId, saved.getPrograms().iterator().next().getId());
        }

        /**
         * Requirement 37: Invalid Course + Program Mismatch Fails
         */
        @Test
        void testBulkUpload_InvalidCourseAndProgram_MismatchFails() throws Exception {
                when(leadAcademicResolver.resolveLeadAcademicMappingsForUpload(eq("MBA"), isNull(), eq("School of Law"),
                                isNull(), isNull(), isNull(), any(), anyInt()))
                                .thenThrow(new BadRequestException(
                                                "COURSE_PROGRAM_MISMATCH: Course 'MBA' is not mapped to Program(s) [School of Law]"));

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (Workbook workbook = new XSSFWorkbook()) {
                        Sheet sheet = workbook.createSheet("Leads");
                        Row header = sheet.createRow(0);
                        header.createCell(0).setCellValue("Full Name");
                        header.createCell(1).setCellValue("Phone Number");
                        header.createCell(2).setCellValue("Course Interested");
                        header.createCell(3).setCellValue("Program");

                        Row row1 = sheet.createRow(1);
                        row1.createCell(0).setCellValue("Student E");
                        row1.createCell(1).setCellValue("+919555555555");
                        row1.createCell(2).setCellValue("MBA");
                        row1.createCell(3).setCellValue("School of Law");

                        workbook.write(out);
                }

                MockMultipartFile file = new MockMultipartFile(
                                "file", "leads.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                out.toByteArray());

                BulkLeadUploadResponse response = bulkUploadService.bulkUploadLeads(
                                file, null, null, null, null, null, null, null, null, null, null, null);

                assertEquals(1, response.getTotalRows());
                assertEquals(0, response.getSuccessCount());
                assertEquals(1, response.getFailedCount());
                assertEquals("program", response.getFailedRows().get(0).getField());
                assertTrue(response.getFailedRows().get(0).getReason().contains("COURSE_PROGRAM_MISMATCH"));

                verify(leadRepository, never()).save(any());
        }

        /**
         * Requirement 38: Reordered Excel Columns produce the same mapping
         */
        @Test
        void testBulkUpload_ReorderedExcelColumns() throws Exception {
                UUID mbaId = UUID.randomUUID();
                Course mbaCourse = Course.builder().courseName("MBA").build();
                mbaCourse.setId(mbaId);

                UUID somId = UUID.randomUUID();
                Program somProgram = Program.builder().name("School of Management").build();
                somProgram.setId(somId);

                when(leadAcademicResolver.resolveLeadAcademicMappingsForUpload(eq("MBA"), isNull(),
                                eq("School of Management"), isNull(), isNull(), isNull(), any(), anyInt()))
                                .thenReturn(LeadAcademicResolutionResult.builder()
                                                .course(mbaCourse)
                                                .program(somProgram)
                                                .programs(Set.of(somProgram))
                                                .interestedCourses(Set.of(mbaCourse))
                                                .courseSource("EXCEL")
                                                .programSource("EXCEL")
                                                .build());

                // Order 1: Program | Full Name | Course Interested | Phone Number
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (Workbook workbook = new XSSFWorkbook()) {
                        Sheet sheet = workbook.createSheet("Leads");
                        Row header = sheet.createRow(0);
                        header.createCell(0).setCellValue("Program");
                        header.createCell(1).setCellValue("Full Name");
                        header.createCell(2).setCellValue("Course Interested");
                        header.createCell(3).setCellValue("Phone Number");

                        Row row1 = sheet.createRow(1);
                        row1.createCell(0).setCellValue("School of Management");
                        row1.createCell(1).setCellValue("Student F");
                        row1.createCell(2).setCellValue("MBA");
                        row1.createCell(3).setCellValue("+919666666666");

                        workbook.write(out);
                }

                MockMultipartFile file = new MockMultipartFile(
                                "file", "leads.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                out.toByteArray());

                ArgumentCaptor<Lead> leadCaptor = ArgumentCaptor.forClass(Lead.class);

                BulkLeadUploadResponse response = bulkUploadService.bulkUploadLeads(
                                file, null, null, null, null, null, null, null, null, null, null, null);

                assertEquals(1, response.getSuccessCount());
                verify(leadRepository).save(leadCaptor.capture());
                Lead saved = leadCaptor.getValue();
                assertEquals("Student F", saved.getFullName());
                assertEquals(mbaId, saved.getCourse().getId());
                assertEquals(somId, saved.getPrograms().iterator().next().getId());
        }

        /**
         * Requirement 26: Unsupported Target Field Throws BadRequestException
         */
        @Test
        void testBulkUpload_UnsupportedTargetField_ThrowsBadRequest() {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (Workbook workbook = new XSSFWorkbook()) {
                        Sheet sheet = workbook.createSheet("Leads");
                        Row header = sheet.createRow(0);
                        header.createCell(0).setCellValue("Col A");
                        Row row1 = sheet.createRow(1);
                        row1.createCell(0).setCellValue("Value A");
                        workbook.write(out);
                } catch (Exception ignored) {
                }

                MockMultipartFile file = new MockMultipartFile(
                                "file", "leads.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                out.toByteArray());

                String mappingJson = "[{\"excelColumn\":\"Col A\",\"targetField\":\"unknownFieldXYZ\"}]";

                BadRequestException ex = assertThrows(BadRequestException.class,
                                () -> bulkUploadService.bulkUploadLeads(file, null, null, null, null, null, null, null,
                                                null, null, null, mappingJson));

                assertTrue(ex.getMessage().contains("UNSUPPORTED_IMPORT_FIELD"));
        }
}
