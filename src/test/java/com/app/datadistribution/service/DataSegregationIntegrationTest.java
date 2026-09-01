package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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

    private void authenticateAsAdmin() {
        User admin = userRepository.findByUsername("superadmin")
                .or(() -> userRepository.findByUsername("admin"))
                .orElseGet(() -> userRepository.findAll().stream().findFirst().orElse(null));

        if (admin != null) {
            UserDetailsImpl userDetails = UserDetailsImpl.build(admin);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, List.of(
                            new SimpleGrantedAuthority("DATA_SEGREGATION_VIEW"),
                            new SimpleGrantedAuthority("DATA_SEGREGATION_USER_ANALYTICS"),
                            new SimpleGrantedAuthority("DATA_SEGREGATION_LEAD_STATUS_ANALYTICS"),
                            new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
    }

    @Test
    @DisplayName("Integration: getCourseTypesSummary returns seeded course types")
    void testGetCourseTypesSummaryIntegration() throws Exception {
        authenticateAsAdmin();

        List<CourseTypeSegregationDTO> summary = segregationService.getCourseTypesSummary();
        assertNotNull(summary);
    }

    @Test
    @DisplayName("Integration: getSegregationMatrix with valid courseType returns non-null matrix")
    void testGetSegregationMatrixIntegration() throws Exception {
        authenticateAsAdmin();

        List<CourseType> courseTypes = courseTypeRepository.findAll();
        if (!courseTypes.isEmpty()) {
            CourseType ct = courseTypes.get(0);
            SegregationMatrixResponseDTO matrix = segregationService.getSegregationMatrix(ct.getId(), null, null, null);
            assertNotNull(matrix);
            assertNotNull(matrix.getSources());
            assertTrue(matrix.getTotalLeads() >= 0);
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
