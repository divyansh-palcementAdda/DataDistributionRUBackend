package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.segregation.DataSegregationCapabilitiesDTO;
import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.impl.DataSegregationPermissionServiceImpl;

public class DataSegregationPermissionServiceTest {

    private DataSegregationPermissionServiceImpl permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new DataSegregationPermissionServiceImpl();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthorities(String... authorities) {
        List<SimpleGrantedAuthority> authList = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken("testUser", null, authList);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @Test
    @DisplayName("Case 1: VIEW, COURSE_TYPE, SOURCE, BOARD (No Grade) -> Grade capability false")
    void testCase1_NoGradePermission() {
        setAuthorities(
                "DATA_SEGREGATION_VIEW",
                "DATA_SEGREGATION_COURSE_TYPE_VIEW",
                "DATA_SEGREGATION_SOURCE_VIEW",
                "DATA_SEGREGATION_BOARD_VIEW"
        );

        DataSegregationCapabilitiesDTO caps = permissionService.getCapabilities();
        assertTrue(caps.isCanView());
        assertFalse(caps.isCanViewFullFlow());
        assertTrue(caps.isCanViewCourseType());
        assertTrue(caps.isCanViewSource());
        assertTrue(caps.isCanViewBoard());
        assertFalse(caps.isCanViewGrade());

        // Grade request must throw UnauthorizedException
        assertThrows(UnauthorizedException.class, () ->
                permissionService.validateMatrixAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        // Board request should pass
        assertDoesNotThrow(() ->
                permissionService.validateMatrixAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null));
    }

    @Test
    @DisplayName("Case 2: VIEW, COURSE_TYPE, SOURCE, BOARD, GRADE -> All true")
    void testCase2_AllHierarchyPermissions() {
        setAuthorities(
                "DATA_SEGREGATION_VIEW",
                "DATA_SEGREGATION_COURSE_TYPE_VIEW",
                "DATA_SEGREGATION_SOURCE_VIEW",
                "DATA_SEGREGATION_BOARD_VIEW",
                "DATA_SEGREGATION_GRADE_VIEW"
        );

        DataSegregationCapabilitiesDTO caps = permissionService.getCapabilities();
        assertTrue(caps.isCanView());
        assertTrue(caps.isCanViewCourseType());
        assertTrue(caps.isCanViewSource());
        assertTrue(caps.isCanViewBoard());
        assertTrue(caps.isCanViewGrade());

        assertDoesNotThrow(() ->
                permissionService.validateMatrixAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("Case 3: VIEW, COURSE_TYPE only -> Source, Board, Grade false")
    void testCase3_CourseTypeOnly() {
        setAuthorities(
                "DATA_SEGREGATION_VIEW",
                "DATA_SEGREGATION_COURSE_TYPE_VIEW"
        );

        DataSegregationCapabilitiesDTO caps = permissionService.getCapabilities();
        assertTrue(caps.isCanView());
        assertTrue(caps.isCanViewCourseType());
        assertFalse(caps.isCanViewSource());
        assertFalse(caps.isCanViewBoard());
        assertFalse(caps.isCanViewGrade());

        // Requesting with leadSourceId must fail
        assertThrows(UnauthorizedException.class, () ->
                permissionService.validateMatrixAccess(UUID.randomUUID(), UUID.randomUUID(), null, null));
    }

    @Test
    @DisplayName("Case 4: No Data Segregation permissions -> Base access denied (403)")
    void testCase4_NoPermissions() {
        setAuthorities("LEAD_READ");

        DataSegregationCapabilitiesDTO caps = permissionService.getCapabilities();
        assertFalse(caps.isCanView());
        assertFalse(caps.isCanViewCourseType());
        assertFalse(caps.isCanViewSource());
        assertFalse(caps.isCanViewBoard());
        assertFalse(caps.isCanViewGrade());

        assertThrows(UnauthorizedException.class, () -> permissionService.validateBaseAccess());
        assertThrows(UnauthorizedException.class, () -> permissionService.validateCourseTypeAccess());
    }

    @Test
    @DisplayName("Case 5: FULL_FLOW_VIEW -> Grants all flow capabilities")
    void testCase5_FullFlowPermission() {
        setAuthorities("DATA_SEGREGATION_FULL_FLOW_VIEW");

        DataSegregationCapabilitiesDTO caps = permissionService.getCapabilities();
        assertTrue(caps.isCanView());
        assertTrue(caps.isCanViewFullFlow());
        assertTrue(caps.isCanViewCourseType());
        assertTrue(caps.isCanViewSource());
        assertTrue(caps.isCanViewBoard());
        assertTrue(caps.isCanViewGrade());
        assertTrue(caps.isCanViewUserAnalytics());
        assertTrue(caps.isCanViewLeadStatusAnalytics());

        assertDoesNotThrow(() ->
                permissionService.validateMatrixAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("Case 6: Direct User entity capability resolution")
    void testCase6_UserEntityResolution() {
        Permission pView = Permission.builder().name(PermissionType.DATA_SEGREGATION_VIEW.name()).active(true).build();
        Permission pCourse = Permission.builder().name(PermissionType.DATA_SEGREGATION_COURSE_TYPE_VIEW.name()).active(true).build();
        Role role = Role.builder().name("TEST_ROLE").active(true).permissions(Set.of(pView, pCourse)).build();
        User user = User.builder().username("test").roles(Set.of(role)).build();

        DataSegregationCapabilitiesDTO caps = permissionService.getCapabilitiesForUser(user);
        assertTrue(caps.isCanView());
        assertTrue(caps.isCanViewCourseType());
        assertFalse(caps.isCanViewSource());
        assertFalse(caps.isCanViewBoard());
        assertFalse(caps.isCanViewGrade());
    }

    @Test
    @DisplayName("Case 7: User analytics dimension validation rejects unauthorized grade")
    void testCase7_AnalyticsUnauthorizedDimension() {
        setAuthorities(
                "DATA_SEGREGATION_VIEW",
                "DATA_SEGREGATION_USER_ANALYTICS",
                "DATA_SEGREGATION_COURSE_TYPE_VIEW",
                "DATA_SEGREGATION_SOURCE_VIEW",
                "DATA_SEGREGATION_BOARD_VIEW"
        );

        UUID ctId = UUID.randomUUID();
        UUID srcId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID grId = UUID.randomUUID();

        // Valid up to Board
        assertDoesNotThrow(() -> permissionService.validateUserAnalyticsAccess(ctId, srcId, bId, null));

        // Unauthorized Grade must throw
        assertThrows(UnauthorizedException.class, () ->
                permissionService.validateUserAnalyticsAccess(ctId, srcId, bId, grId));
    }
}
