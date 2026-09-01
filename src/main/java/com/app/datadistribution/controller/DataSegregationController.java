package com.app.datadistribution.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.segregation.CourseTypeSegregationDTO;
import com.app.datadistribution.dto.segregation.LeadStatusAnalyticsDTO;
import com.app.datadistribution.dto.segregation.SegregationMatrixResponseDTO;
import com.app.datadistribution.dto.segregation.UserSegregationAnalyticsDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.IDataSegregationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/data-segregation")
@RequiredArgsConstructor
@Tag(name = "Data Segregation Management", description = "Endpoints for hierarchical lead segregation matrix and deep analytics")
public class DataSegregationController {

    private final IDataSegregationService segregationService;

    @GetMapping("/course-types")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DASHBOARD_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get active course types with total lead counts within data scope")
    public ResponseEntity<ApiResponse<List<CourseTypeSegregationDTO>>> getCourseTypes()
            throws UnauthorizedException, BadRequestException {
        List<CourseTypeSegregationDTO> response = segregationService.getCourseTypesSummary();
        return ResponseEntity.ok(ApiResponse.success("Course types summary retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/matrix")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DASHBOARD_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get hierarchical lead segregation matrix (Course Type -> Source -> Board -> Grade)")
    public ResponseEntity<ApiResponse<SegregationMatrixResponseDTO>> getMatrix(
            @RequestParam(name = "courseTypeId") UUID courseTypeId,
            @RequestParam(name = "leadSourceId", required = false) UUID leadSourceId,
            @RequestParam(name = "boardId", required = false) UUID boardId,
            @RequestParam(name = "gradeId", required = false) UUID gradeId)
            throws UnauthorizedException, BadRequestException {
        SegregationMatrixResponseDTO response = segregationService.getSegregationMatrix(courseTypeId, leadSourceId, boardId, gradeId);
        return ResponseEntity.ok(ApiResponse.success("Data segregation matrix retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/user-analytics")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_USER_ANALYTICS') or hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DASHBOARD_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get user-level breakdown and dynamic lead status counts for a selected segregation scope")
    public ResponseEntity<ApiResponse<UserSegregationAnalyticsDTO>> getUserAnalytics(
            @RequestParam(name = "courseTypeId") UUID courseTypeId,
            @RequestParam(name = "leadSourceId") UUID leadSourceId,
            @RequestParam(name = "boardId", required = false) UUID boardId,
            @RequestParam(name = "gradeId", required = false) UUID gradeId)
            throws UnauthorizedException, BadRequestException {
        UserSegregationAnalyticsDTO response = segregationService.getUserAnalytics(courseTypeId, leadSourceId, boardId, gradeId);
        return ResponseEntity.ok(ApiResponse.success("User segregation analytics retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/lead-status-analytics")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_LEAD_STATUS_ANALYTICS') or hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DASHBOARD_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get dynamic lead status analytics matrix for a selected segregation scope")
    public ResponseEntity<ApiResponse<List<LeadStatusAnalyticsDTO>>> getLeadStatusAnalytics(
            @RequestParam(name = "courseTypeId") UUID courseTypeId,
            @RequestParam(name = "leadSourceId") UUID leadSourceId,
            @RequestParam(name = "boardId", required = false) UUID boardId,
            @RequestParam(name = "gradeId", required = false) UUID gradeId)
            throws UnauthorizedException, BadRequestException {
        List<LeadStatusAnalyticsDTO> response = segregationService.getLeadStatusAnalytics(courseTypeId, leadSourceId, boardId, gradeId);
        return ResponseEntity.ok(ApiResponse.success("Lead status analytics retrieved successfully", response, HttpStatus.OK.value()));
    }
}
