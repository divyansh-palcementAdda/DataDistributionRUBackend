package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.segregation.CourseTypeSegregationDTO;
import com.app.datadistribution.dto.segregation.LeadStatusAnalyticsDTO;
import com.app.datadistribution.dto.segregation.SegregationMatrixResponseDTO;
import com.app.datadistribution.dto.segregation.UserSegregationAnalyticsDTO;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.security.UserDetailsImpl;
import com.app.datadistribution.service.interfaces.IDataSegregationService;

@SpringBootTest
@Transactional
public class DataSegregationIntegrationTest {

    @Autowired
    private IDataSegregationService segregationService;

    @Autowired
    private CourseTypeRepository courseTypeRepository;

    @Autowired
    private LeadSourceRepository leadSourceRepository;

    @Autowired
    private UserRepository userRepository;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        authenticateAsAdmin();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWithAuthorities(String... authorities) {
        User admin = userRepository.findByUsername("superadmin")
                .or(() -> userRepository.findByUsername("admin"))
                .orElseGet(() -> {
                    User u = User.builder()
                            .username("superadmin")
                            .email("superadmin@test.com")
                            .password("password")
                            .active(true)
                            .emailVerified(true)
                            .build();
                    return userRepository.save(u);
                });

        UserDetailsImpl userDetails = UserDetailsImpl.build(admin);
        List<SimpleGrantedAuthority> authList = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, authList
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void authenticateAsAdmin() {
        authenticateWithAuthorities(
                "DATA_SEGREGATION_VIEW",
                "DATA_SEGREGATION_FULL_FLOW_VIEW",
                "DATA_SEGREGATION_COURSE_TYPE_VIEW",
                "DATA_SEGREGATION_SOURCE_VIEW",
                "DATA_SEGREGATION_BOARD_VIEW",
                "DATA_SEGREGATION_GRADE_VIEW",
                "DATA_SEGREGATION_USER_ANALYTICS",
                "DATA_SEGREGATION_LEAD_STATUS_ANALYTICS",
                "ROLE_SUPER_ADMIN"
        );
    }

    @Test
    @DisplayName("Integration: getCourseTypesSummary returns seeded course types")
    void testGetCourseTypesSummaryIntegration() throws Exception {
        authenticateAsAdmin();

        List<CourseTypeSegregationDTO> summary = segregationService.getCourseTypesSummary();
        assertNotNull(summary);
    }

    @Test
    @DisplayName("Integration: getSegregationMatrix with valid courseType returns non-null matrix with full flow")
    void testGetSegregationMatrixIntegration() throws Exception {
        authenticateAsAdmin();

        List<CourseType> courseTypes = courseTypeRepository.findAll();
        if (!courseTypes.isEmpty()) {
            CourseType ct = courseTypes.get(0);
            SegregationMatrixResponseDTO matrix = segregationService.getSegregationMatrix(ct.getId(), null, null, null);
            assertNotNull(matrix);
            assertNotNull(matrix.getSources());
            assertNotNull(matrix.getCapabilities());
            assertTrue(matrix.getCapabilities().isCanViewFullFlow());
            assertTrue(matrix.getTotalLeads() >= 0);
        }
    }

    @Test
    @DisplayName("Integration: Partial permissions without Grade hides grade tree")
    void testPartialPermissionsWithoutGrade() throws Exception {
        authenticateWithAuthorities(
                "DATA_SEGREGATION_VIEW",
                "DATA_SEGREGATION_COURSE_TYPE_VIEW",
                "DATA_SEGREGATION_SOURCE_VIEW",
                "DATA_SEGREGATION_BOARD_VIEW"
        );

        List<CourseType> courseTypes = courseTypeRepository.findAll();
        if (!courseTypes.isEmpty()) {
            CourseType ct = courseTypes.get(0);
            SegregationMatrixResponseDTO matrix = segregationService.getSegregationMatrix(ct.getId(), null, null, null);
            assertNotNull(matrix);
            assertNotNull(matrix.getCapabilities());
            assertTrue(matrix.getCapabilities().isCanViewBoard());
            assertTrue(!matrix.getCapabilities().isCanViewGrade());

            // Verify no grades exist in any board node
            matrix.getSources().forEach(src -> {
                src.getBoards().forEach(board -> {
                    assertEquals(0, board.getGrades().size(), "Grades must not be returned when grade permission is missing");
                });
            });

            // Passing gradeId must be rejected
            assertThrows(UnauthorizedException.class, () ->
                    segregationService.getSegregationMatrix(ct.getId(), null, null, UUID.randomUUID()));
        }
    }

    @Test
    @DisplayName("Integration: getUserAnalytics and getLeadStatusAnalytics execute without error")
    void testAnalyticsIntegration() throws Exception {
        authenticateAsAdmin();

        List<CourseType> courseTypes = courseTypeRepository.findAll();
        List<LeadSource> sources = leadSourceRepository.findAll();

        if (!courseTypes.isEmpty() && !sources.isEmpty()) {
            CourseType ct = courseTypes.get(0);
            LeadSource src = sources.get(0);

            UserSegregationAnalyticsDTO userAnalytics = segregationService.getUserAnalytics(ct.getId(), src.getId(), null, null);
            assertNotNull(userAnalytics);
            assertNotNull(userAnalytics.getStatusColumns());
            assertNotNull(userAnalytics.getUsers());

            List<LeadStatusAnalyticsDTO> statusAnalytics = segregationService.getLeadStatusAnalytics(ct.getId(), src.getId(), null, null);
            assertNotNull(statusAnalytics);
        }
    }
}
