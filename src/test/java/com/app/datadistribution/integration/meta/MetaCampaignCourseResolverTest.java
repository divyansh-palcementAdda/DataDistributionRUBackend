package com.app.datadistribution.integration.meta;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.datadistribution.entity.Course;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.integration.meta.service.MetaCampaignCourseResolver;
import com.app.datadistribution.integration.meta.service.MetaCampaignCourseResolver.CourseResolutionResult;
import com.app.datadistribution.repository.CourseRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaCampaignCourseResolverTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private MetaCampaignCourseResolver resolver;

    private Course mbaCourse;
    private Course bbaCourse;
    private Course mcaCourse;
    private Course btechCourse;
    private Course btechCseCourse;

    @BeforeEach
    void setUp() {
        mbaCourse = Course.builder()
                .courseName("MBA")
                .courseCode("MBA")
                .status(Status.ACTIVE)
                .build();
        mbaCourse.setId(UUID.randomUUID());

        bbaCourse = Course.builder()
                .courseName("BBA")
                .courseCode("BBA")
                .status(Status.ACTIVE)
                .build();
        bbaCourse.setId(UUID.randomUUID());

        mcaCourse = Course.builder()
                .courseName("MCA")
                .courseCode("MCA")
                .status(Status.ACTIVE)
                .build();
        mcaCourse.setId(UUID.randomUUID());

        btechCourse = Course.builder()
                .courseName("B.Tech")
                .courseCode("B.TECH")
                .status(Status.ACTIVE)
                .build();
        btechCourse.setId(UUID.randomUUID());

        btechCseCourse = Course.builder()
                .courseName("B.Tech Computer Science")
                .courseCode("B.TECH_CSE")
                .status(Status.ACTIVE)
                .build();
        btechCseCourse.setId(UUID.randomUUID());
    }

    private void mockActiveCourses() {
        when(courseRepository.findByStatusAndIsDeletedFalseOrderByCourseNameAsc(Status.ACTIVE))
                .thenReturn(List.of(bbaCourse, btechCourse, btechCseCourse, mbaCourse, mcaCourse));
    }

    @Test
    @DisplayName("Should extract MBA from 'MBA Admission 2027'")
    void shouldExtractMbaFromCampaign() {
        mockActiveCourses();
        CourseResolutionResult result = resolver.resolveCourse("MBA Admission 2027");

        assertTrue(result.isSuccess());
        assertNotNull(result.getCourse());
        assertEquals("MBA", result.getCourse().getCourseName());
        assertEquals(mbaCourse.getId(), result.getCourse().getId());
    }

    @Test
    @DisplayName("Should extract BBA from 'BBA Lead Generation'")
    void shouldExtractBbaFromCampaign() {
        mockActiveCourses();
        CourseResolutionResult result = resolver.resolveCourse("BBA Lead Generation");

        assertTrue(result.isSuccess());
        assertEquals("BBA", result.getCourse().getCourseName());
        assertEquals(bbaCourse.getId(), result.getCourse().getId());
    }

    @Test
    @DisplayName("Should extract MCA from 'MCA Admissions Campaign'")
    void shouldExtractMcaFromCampaign() {
        mockActiveCourses();
        CourseResolutionResult result = resolver.resolveCourse("MCA Admissions Campaign");

        assertTrue(result.isSuccess());
        assertEquals("MCA", result.getCourse().getCourseName());
        assertEquals(mcaCourse.getId(), result.getCourse().getId());
    }

    @Test
    @DisplayName("Should extract B.Tech from 'B.Tech Admission 2027' and 'B.Tech Admission Indore'")
    void shouldExtractBtechFromCampaign() {
        mockActiveCourses();

        CourseResolutionResult res1 = resolver.resolveCourse("B.Tech Admission 2027");
        assertTrue(res1.isSuccess());
        assertEquals("B.Tech", res1.getCourse().getCourseName());

        CourseResolutionResult res2 = resolver.resolveCourse("B.Tech Admission Indore");
        assertTrue(res2.isSuccess());
        assertEquals("B.Tech", res2.getCourse().getCourseName());
    }

    @Test
    @DisplayName("Should extract B.Tech from spelling variations like 'BTech Admission 2027'")
    void shouldExtractBtechFromVariations() {
        mockActiveCourses();

        CourseResolutionResult result = resolver.resolveCourse("BTech Admission 2027");
        assertTrue(result.isSuccess());
        assertEquals("B.Tech", result.getCourse().getCourseName());
    }

    @Test
    @DisplayName("Should resolve MBA via configured alias 'Master of Business Administration'")
    void shouldResolveViaAlias() {
        mockActiveCourses();

        CourseResolutionResult result = resolver.resolveCourse("Master of Business Administration Admissions");
        assertTrue(result.isSuccess());
        assertEquals("MBA", result.getCourse().getCourseName());
    }

    @Test
    @DisplayName("Should match case-insensitively")
    void shouldMatchCaseInsensitively() {
        mockActiveCourses();

        CourseResolutionResult result = resolver.resolveCourse("mba admission 2027");
        assertTrue(result.isSuccess());
        assertEquals("MBA", result.getCourse().getCourseName());
    }

    @Test
    @DisplayName("Should return AMBIGUOUS when multiple distinct courses are detected")
    void shouldReturnAmbiguousWhenMultipleCoursesPresent() {
        mockActiveCourses();

        CourseResolutionResult result = resolver.resolveCourse("Combined MBA and BBA Admissions 2027");
        assertFalse(result.isSuccess());
        assertTrue(result.isAmbiguous());
        assertNull(result.getCourse());
        assertEquals(CourseResolutionResult.Status.AMBIGUOUS, result.getStatus());
    }

    @Test
    @DisplayName("Subsumption: Should resolve specific 'B.Tech Computer Science' over generic 'B.Tech'")
    void shouldResolveSpecificCourseOverSubsumedGeneric() {
        mockActiveCourses();

        CourseResolutionResult result = resolver.resolveCourse("B.Tech Computer Science Admissions 2027");
        assertTrue(result.isSuccess());
        assertEquals("B.Tech Computer Science", result.getCourse().getCourseName());
        assertEquals(btechCseCourse.getId(), result.getCourse().getId());
    }

    @Test
    @DisplayName("Boundary check: Should not match embedded substrings like 'Hubballi' for BBA or 'Columbia' for MBA")
    void shouldNotMatchEmbeddedSubstrings() {
        mockActiveCourses();

        CourseResolutionResult res1 = resolver.resolveCourse("Hubballi Campus Direct Walk-in 2027");
        assertFalse(res1.isSuccess());
        assertEquals(CourseResolutionResult.Status.NO_MATCH, res1.getStatus());

        CourseResolutionResult res2 = resolver.resolveCourse("Columbia Partner Conference 2027");
        assertFalse(res2.isSuccess());
        assertEquals(CourseResolutionResult.Status.NO_MATCH, res2.getStatus());
    }

    @Test
    @DisplayName("Should return NO_MATCH when campaign name is null or blank")
    void shouldHandleNullOrBlank() {
        CourseResolutionResult resNull = resolver.resolveCourse(null);
        assertEquals(CourseResolutionResult.Status.NO_MATCH, resNull.getStatus());

        CourseResolutionResult resEmpty = resolver.resolveCourse("   ");
        assertEquals(CourseResolutionResult.Status.NO_MATCH, resEmpty.getStatus());
    }

    @Test
    @DisplayName("Should return NO_MATCH for unknown courses")
    void shouldReturnNoMatchForUnknownCourse() {
        mockActiveCourses();

        CourseResolutionResult result = resolver.resolveCourse("Digital Marketing Bootcamp November");
        assertEquals(CourseResolutionResult.Status.NO_MATCH, result.getStatus());
        assertNull(result.getCourse());
    }
}

