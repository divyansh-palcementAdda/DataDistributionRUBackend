package com.app.datadistribution.service;

import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.ProgramRepository;
import com.app.datadistribution.service.util.ProgramCourseResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProgramCourseMappingTest {

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private ProgramCourseResolver programCourseResolver;

    private Program programSOM;
    private Program programSOET;
    private Course courseMBA;
    private Course courseBTech;
    private Course courseLLB;

    private UUID somId;
    private UUID soetId;
    private UUID mbaId;
    private UUID btechId;
    private UUID llbId;

    @BeforeEach
    void setUp() {
        somId = UUID.randomUUID();
        soetId = UUID.randomUUID();
        mbaId = UUID.randomUUID();
        btechId = UUID.randomUUID();
        llbId = UUID.randomUUID();

        courseMBA = Course.builder()
                .courseName("Master of Business Administration")
                .courseCode("MBA")
                .status(Status.ACTIVE)
                .build();
        courseMBA.setId(mbaId);

        courseBTech = Course.builder()
                .courseName("Bachelor of Technology in CS")
                .courseCode("BTECH_CS")
                .status(Status.ACTIVE)
                .build();
        courseBTech.setId(btechId);

        courseLLB = Course.builder()
                .courseName("Bachelor of Legislative Law")
                .courseCode("LLB")
                .status(Status.ACTIVE)
                .build();
        courseLLB.setId(llbId);

        programSOM = Program.builder()
                .name("School of Management")
                .code("SOM")
                .status(Status.ACTIVE)
                .courses(new HashSet<>(List.of(courseMBA)))
                .build();
        programSOM.setId(somId);

        programSOET = Program.builder()
                .name("School of Engineering & Technology")
                .code("SOET")
                .status(Status.ACTIVE)
                .courses(new HashSet<>(List.of(courseBTech)))
                .build();
        programSOET.setId(soetId);
    }

    @Test
    @DisplayName("Should successfully validate course mapped to program")
    void testResolveAndValidate_Success() throws BadRequestException {
        when(programRepository.findById(somId)).thenReturn(Optional.of(programSOM));
        when(programRepository.isCourseMappedToAnyProgram(anyCollection(), eq(mbaId))).thenReturn(true);

        Program result = programCourseResolver.resolveAndValidate(somId, mbaId, List.of(mbaId));

        assertNotNull(result);
        assertEquals("School of Management", result.getName());
    }

    @Test
    @DisplayName("Should reject course that is NOT mapped to the selected program")
    void testResolveAndValidate_CourseNotMapped_ThrowsBadRequestException() {
        when(programRepository.findById(somId)).thenReturn(Optional.of(programSOM));
        when(programRepository.isCourseMappedToAnyProgram(anyCollection(), eq(btechId))).thenReturn(false);
        when(courseRepository.findById(btechId)).thenReturn(Optional.of(courseBTech));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                programCourseResolver.resolveAndValidate(somId, btechId, null)
        );

        assertTrue(ex.getMessage().contains("is not mapped to selected Program(s)"));
        assertTrue(ex.getMessage().contains("Bachelor of Technology in CS"));
        assertTrue(ex.getMessage().contains("School of Management"));
    }

    @Test
    @DisplayName("Should reject interested course not mapped to the program")
    void testResolveAndValidate_InterestedCourseNotMapped_ThrowsBadRequestException() {
        when(programRepository.findById(somId)).thenReturn(Optional.of(programSOM));
        when(programRepository.isCourseMappedToAnyProgram(anyCollection(), eq(mbaId))).thenReturn(true);
        when(programRepository.isCourseMappedToAnyProgram(anyCollection(), eq(llbId))).thenReturn(false);
        when(courseRepository.findById(llbId)).thenReturn(Optional.of(courseLLB));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                programCourseResolver.resolveAndValidate(somId, mbaId, List.of(mbaId, llbId))
        );

        assertTrue(ex.getMessage().contains("Interested Course"));
        assertTrue(ex.getMessage().contains("Bachelor of Legislative Law"));
    }

    @Test
    @DisplayName("Should resolve program by case-insensitive name or code")
    void testResolveProgramByNameOrCode() {
        when(programRepository.findByNameIgnoreCaseAndIsDeletedFalse("School of Management")).thenReturn(Optional.of(programSOM));
        when(programRepository.findByCodeIgnoreCaseAndIsDeletedFalse("som")).thenReturn(Optional.of(programSOM));

        Optional<Program> byName = programCourseResolver.resolveProgramByNameOrCode(" School of Management ");
        Optional<Program> byCode = programCourseResolver.resolveProgramByNameOrCode("som");

        assertTrue(byName.isPresent());
        assertEquals("SOM", byName.get().getCode());

        assertTrue(byCode.isPresent());
        assertEquals("School of Management", byCode.get().getName());
    }

    @Test
    @DisplayName("Should resolve course by case-insensitive name or code")
    void testResolveCourseByNameOrCode() {
        when(courseRepository.findByCourseNameIgnoreCaseAndIsDeletedFalse("Master of Business Administration")).thenReturn(Optional.of(courseMBA));
        when(courseRepository.findByCourseCodeIgnoreCaseAndIsDeletedFalse("mba")).thenReturn(Optional.of(courseMBA));

        Optional<Course> byName = programCourseResolver.resolveCourseByNameOrCode(" Master of Business Administration ");
        Optional<Course> byCode = programCourseResolver.resolveCourseByNameOrCode("mba");

        assertTrue(byName.isPresent());
        assertEquals("MBA", byName.get().getCourseCode());

        assertTrue(byCode.isPresent());
        assertEquals("Master of Business Administration", byCode.get().getCourseName());
    }

    @Test
    @DisplayName("Should successfully validate when multiple programs are selected and course belongs to one of them")
    void testResolveMultiPrograms_Success() throws BadRequestException {
        when(programRepository.findById(somId)).thenReturn(Optional.of(programSOM));
        when(programRepository.findById(soetId)).thenReturn(Optional.of(programSOET));
        when(programRepository.isCourseMappedToAnyProgram(anySet(), eq(mbaId))).thenReturn(true);

        Set<Program> result = programCourseResolver.resolveLeadProgramCourseRelationship(
                List.of(somId, soetId), mbaId, null);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(programSOM));
        assertTrue(result.contains(programSOET));
    }

    @Test
    @DisplayName("Should reject course if it is not mapped to any of the selected multiple programs")
    void testResolveMultiPrograms_CourseMismatch_ThrowsBadRequestException() {
        when(programRepository.findById(soetId)).thenReturn(Optional.of(programSOET));
        when(programRepository.isCourseMappedToAnyProgram(anySet(), eq(mbaId))).thenReturn(false);
        when(courseRepository.findById(mbaId)).thenReturn(Optional.of(courseMBA));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                programCourseResolver.resolveLeadProgramCourseRelationship(List.of(soetId), mbaId, null)
        );

        assertTrue(ex.getMessage().contains("COURSE_PROGRAM_MISMATCH"));
        assertTrue(ex.getMessage().contains("Master of Business Administration"));
    }

    @Test
    @DisplayName("Should auto-resolve program when course is selected directly without programs")
    void testCourseSelectedDirectly_AutoResolvesPrograms() throws BadRequestException {
        when(programRepository.findActiveProgramsByCourseId(mbaId)).thenReturn(List.of(programSOM));

        Set<Program> result = programCourseResolver.resolveLeadProgramCourseRelationship(
                Collections.emptyList(), mbaId, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.contains(programSOM));
    }

    @Test
    @DisplayName("Should auto-resolve all programs when course is mapped to multiple programs")
    void testCourseMappedToMultiplePrograms_AutoResolvesAll() throws BadRequestException {
        when(programRepository.findActiveProgramsByCourseId(mbaId)).thenReturn(List.of(programSOM, programSOET));

        Set<Program> result = programCourseResolver.resolveLeadProgramCourseRelationship(
                Collections.emptyList(), mbaId, null);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(programSOM));
        assertTrue(result.contains(programSOET));
    }

    @Test
    @DisplayName("Should return empty set when neither program nor course is selected")
    void testNoProgramNoCourse_ReturnsEmptySet() throws BadRequestException {
        Set<Program> result = programCourseResolver.resolveLeadProgramCourseRelationship(
                Collections.emptyList(), null, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should resolve multiple programs from comma-separated string for bulk upload")
    void testResolveProgramsByNameOrCodes_CommaSeparated() throws BadRequestException {
        when(programRepository.findByNameIgnoreCaseAndIsDeletedFalse("School of Management")).thenReturn(Optional.of(programSOM));
        when(programRepository.findByNameIgnoreCaseAndIsDeletedFalse("School of Engineering & Technology")).thenReturn(Optional.of(programSOET));

        Set<Program> result = programCourseResolver.resolveProgramsByNameOrCodes(
                List.of("School of Management, School of Engineering & Technology"));

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(programSOM));
        assertTrue(result.contains(programSOET));
    }
}
