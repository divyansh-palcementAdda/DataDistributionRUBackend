package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.infopanel.CompetitorComparisonDTO;
import com.app.datadistribution.dto.infopanel.CompetitorRequestDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelRequestDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelResponseDTO;
import com.app.datadistribution.dto.infopanel.InfoPanelPermissionMatrixDTO;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseInfoPanel;
import com.app.datadistribution.entity.CourseInfoPanelCompetitor;
import com.app.datadistribution.entity.CourseInfoPanelCompetitorBranch;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.repository.CompetitorCourseComparisonRepository;
import com.app.datadistribution.repository.CourseInfoPanelCompetitorBranchRepository;
import com.app.datadistribution.repository.CourseInfoPanelCompetitorRepository;
import com.app.datadistribution.repository.CourseInfoPanelRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.service.impl.CourseInfoPanelServiceImpl;
import com.app.datadistribution.service.impl.InfoPanelSecurityServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;

class CourseInfoPanelServiceTest {

    private CourseInfoPanelRepository infoPanelRepository;
    private CourseInfoPanelCompetitorRepository competitorRepository;
    private CourseInfoPanelCompetitorBranchRepository branchRepository;
    private CompetitorCourseComparisonRepository comparisonRepository;
    private CourseRepository courseRepository;
    private LeadRepository leadRepository;
    private InfoPanelSecurityServiceImpl securityService;
    private ILeadDataScopeService leadDataScopeService;
    private CourseInfoPanelServiceImpl service;

    private Course mockCourse;
    private UUID courseId;

    @BeforeEach
    void setUp() {
        infoPanelRepository = mock(CourseInfoPanelRepository.class);
        competitorRepository = mock(CourseInfoPanelCompetitorRepository.class);
        branchRepository = mock(CourseInfoPanelCompetitorBranchRepository.class);
        comparisonRepository = mock(CompetitorCourseComparisonRepository.class);
        courseRepository = mock(CourseRepository.class);
        leadRepository = mock(LeadRepository.class);
        leadDataScopeService = mock(ILeadDataScopeService.class);
        securityService = new InfoPanelSecurityServiceImpl();

        service = new CourseInfoPanelServiceImpl(
                infoPanelRepository,
                competitorRepository,
                branchRepository,
                comparisonRepository,
                courseRepository,
                leadRepository,
                securityService,
                leadDataScopeService
        );

        courseId = UUID.randomUUID();
        CourseType courseType = CourseType.builder().name("Management").build();
        mockCourse = Course.builder()
                .courseName("BBA")
                .courseCode("BBA01")
                .status(Status.ACTIVE)
                .fees(95000.0)
                .duration(3)
                .durationUnit("Years")
                .courseType(courseType)
                .build();
        mockCourse.setId(courseId);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(mockCourse));
    }

    @Test
    @DisplayName("Admin can view all permitted fields and competitors")
    void testAdminGetsFullInfoPanel() {
        // Authenticate as Admin
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@test.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        UUID panelId = UUID.randomUUID();
        CourseInfoPanel panel = CourseInfoPanel.builder()
                .course(mockCourse)
                .academicSession("2026-27")
                .school("School of Management")
                .courseName("BBA")
                .courseFee("₹ 95,000 / Year")
                .duration("3 Years")
                .eligibility("10+2 with minimum 50%")
                .jobOpportunities("Business Analyst, HR Executive")
                .hostelFee("₹ 80,000 / Year")
                .courseDetails("Comprehensive BBA Curriculum")
                .courseSpecialities("Harvard Business Cases")
                .renaissanceUniversityUsps("30-acre lush green campus")
                .howWeAreDifferent("Experiential learning")
                .callerGuidance("Highlight campus facilities and internships")
                .active(true)
                .build();
        panel.setId(panelId);

        when(infoPanelRepository.findByCourseIdAndAcademicSessionAndIsDeletedFalse(courseId, "2026-27"))
                .thenReturn(Optional.of(panel));
        when(infoPanelRepository.findById(panelId)).thenReturn(Optional.of(panel));

        UUID compId = UUID.randomUUID();
        CourseInfoPanelCompetitor comp = CourseInfoPanelCompetitor.builder()
                .infoPanel(panel)
                .collegeName("Competitor College A")
                .displayOrder(0)
                .active(true)
                .build();
        comp.setId(compId);

        when(competitorRepository.findByInfoPanelIdAndIsDeletedFalseOrderByDisplayOrderAsc(panelId))
                .thenReturn(List.of(comp));

        CourseInfoPanelCompetitorBranch b1 = CourseInfoPanelCompetitorBranch.builder().branchName("Indore").build();
        CourseInfoPanelCompetitorBranch b2 = CourseInfoPanelCompetitorBranch.builder().branchName("Bhopal").build();
        when(branchRepository.findByCompetitorIdAndIsDeletedFalse(compId)).thenReturn(List.of(b1, b2));

        CourseInfoPanelResponseDTO res = service.getInfoPanelByCourseId(courseId, "2026-27");
        assertNotNull(res);
        assertEquals("BBA", res.getCourseName());
        assertEquals("₹ 95,000 / Year", res.getCourseFee());
        assertEquals("3 Years", res.getDuration());
        assertEquals("10+2 with minimum 50%", res.getEligibility());
        assertEquals("School of Management", res.getSchool());
        assertEquals(1, res.getCompetitors().size());
        assertEquals("Competitor College A", res.getCompetitors().get(0).getCollegeName());
        assertEquals(2, res.getCompetitors().get(0).getBranches().size());
        assertTrue(res.getCompetitors().get(0).getBranches().contains("Indore"));
        assertTrue(res.getCompetitors().get(0).getBranches().contains("Bhopal"));
    }

    @Test
    @DisplayName("Counselor with restricted permissions has sensitive fields sanitized")
    void testCounselorFieldSanitization() {
        // Authenticate as Counselor with restricted permissions (lacks COMPETITOR_FEE_READ and CALLER_GUIDANCE_READ)
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("counselor@test.com", "pass", List.of(
                        new SimpleGrantedAuthority("INFO_PANEL_VIEW"),
                        new SimpleGrantedAuthority(PermissionType.INFO_PANEL_FIELD_COURSE_FEE_READ.name()),
                        new SimpleGrantedAuthority(PermissionType.INFO_PANEL_FIELD_COLLEGE_NAME_READ.name())
                ))
        );

        UUID panelId = UUID.randomUUID();
        CourseInfoPanel panel = CourseInfoPanel.builder()
                .course(mockCourse)
                .academicSession("2026-27")
                .courseName("BBA")
                .courseFee("₹ 95,000 / Year")
                .callerGuidance("Secret Caller Script")
                .active(true)
                .build();
        panel.setId(panelId);

        when(infoPanelRepository.findByCourseIdAndAcademicSessionAndIsDeletedFalse(courseId, "2026-27"))
                .thenReturn(Optional.of(panel));
        when(infoPanelRepository.findById(panelId)).thenReturn(Optional.of(panel));

        CourseInfoPanelResponseDTO res = service.getInfoPanelByCourseId(courseId, "2026-27");
        assertNotNull(res);
        // Permitted field is present
        assertEquals("₹ 95,000 / Year", res.getCourseFee());
        // Unpermitted field is nullified (sanitized)
        assertNull(res.getCallerGuidance(), "Caller guidance must be nullified for user without read permission");
    }

    @Test
    @DisplayName("Permission matrix contains Course Information and Competitor Information groups")
    void testPermissionMatrixStructure() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@test.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        InfoPanelPermissionMatrixDTO matrix = service.getPermissionMatrix();
        assertNotNull(matrix);
        assertEquals("INFO_PANEL", matrix.getEntity());
        assertEquals(2, matrix.getGroups().size());

        InfoPanelPermissionMatrixDTO.FieldGroupDTO courseGroup = matrix.getGroups().stream()
                .filter(g -> "COURSE_INFORMATION".equals(g.getKey()))
                .findFirst()
                .orElse(null);
        assertNotNull(courseGroup);
        assertTrue(courseGroup.getFields().stream().anyMatch(f -> "courseFee".equals(f.getKey())));
        assertTrue(courseGroup.getFields().stream().anyMatch(f -> "callerGuidance".equals(f.getKey())));

        InfoPanelPermissionMatrixDTO.FieldGroupDTO compGroup = matrix.getGroups().stream()
                .filter(g -> "COMPETITOR_INFORMATION".equals(g.getKey()))
                .findFirst()
                .orElse(null);
        assertNotNull(compGroup);
        assertTrue(compGroup.getFields().stream().anyMatch(f -> "collegeName".equals(f.getKey())));
        assertTrue(compGroup.getFields().stream().anyMatch(f -> "branches".equals(f.getKey())));
        assertTrue(compGroup.getFields().stream().anyMatch(f -> "averagePlacements".equals(f.getKey())));
    }
}
