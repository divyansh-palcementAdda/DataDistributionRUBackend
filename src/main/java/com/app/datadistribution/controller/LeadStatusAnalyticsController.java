package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.analytics.CourseStatusAnalyticsResponseDTO;
import com.app.datadistribution.dto.analytics.CourseUserStatusAnalyticsResponseDTO;
import com.app.datadistribution.dto.analytics.LeadStatusAnalyticsFilterRequest;
import com.app.datadistribution.dto.analytics.UserStatusAnalyticsResponseDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.ILeadStatusAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/leads/analytics", "/api/lead/analytics"})
@RequiredArgsConstructor
@Tag(name = "Lead Status Analytics", description = "Course-wise and User-wise Lead Status Analytics across Data Distribution System")
public class LeadStatusAnalyticsController {

    private final ILeadStatusAnalyticsService analyticsService;

    @GetMapping("/course-status")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_COURSE_USER_STATUS_ANALYTICS_VIEW') or hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Course-wise Lead Status analytics with dynamic columns and server-side pagination")
    public ResponseEntity<ApiResponse<CourseStatusAnalyticsResponseDTO>> getCourseWiseStatusCounts(
            @ModelAttribute LeadStatusAnalyticsFilterRequest filter)
            throws UnauthorizedException, BadRequestException {
        CourseStatusAnalyticsResponseDTO response = analyticsService.getCourseWiseStatusCounts(filter);
        return ResponseEntity.ok(ApiResponse.success("Course-wise lead status analytics retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/user-status")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_COURSE_USER_STATUS_ANALYTICS_VIEW') or hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get User-wise Lead Status analytics with dynamic columns and server-side pagination")
    public ResponseEntity<ApiResponse<UserStatusAnalyticsResponseDTO>> getUserWiseStatusCounts(
            @ModelAttribute LeadStatusAnalyticsFilterRequest filter)
            throws UnauthorizedException, BadRequestException {
        UserStatusAnalyticsResponseDTO response = analyticsService.getUserWiseStatusCounts(filter);
        return ResponseEntity.ok(ApiResponse.success("User-wise lead status analytics retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/course-user-status")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_COURSE_USER_STATUS_ANALYTICS_VIEW') or hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get combined Course-wise & User-wise Lead Status analytics")
    public ResponseEntity<ApiResponse<CourseUserStatusAnalyticsResponseDTO>> getCourseUserStatusAnalytics(
            @ModelAttribute LeadStatusAnalyticsFilterRequest filter)
            throws UnauthorizedException, BadRequestException {
        CourseUserStatusAnalyticsResponseDTO response = analyticsService.getCourseUserStatusAnalytics(filter);
        return ResponseEntity.ok(ApiResponse.success("Course and User lead status analytics retrieved successfully", response, HttpStatus.OK.value()));
    }
}
