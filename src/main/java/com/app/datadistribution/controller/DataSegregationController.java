package com.app.datadistribution.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.segregation.CourseSegregationResponseDTO;
import com.app.datadistribution.dto.segregation.CourseUserSegregationResponseDTO;
import com.app.datadistribution.dto.segregation.DataSegregationCapabilitiesDTO;
import com.app.datadistribution.dto.segregation.CourseTypeSegregationDTO;
import com.app.datadistribution.dto.segregation.LeadStatusAnalyticsDTO;
import com.app.datadistribution.dto.segregation.SegregationMatrixResponseDTO;
import com.app.datadistribution.dto.segregation.UserSegregationAnalyticsDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.IDataSegregationService;

import com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest;
import com.app.datadistribution.dto.segregation.UserAllocationSummaryDTO;
import com.app.datadistribution.dto.segregation.UserAllocationUsersResponseDTO;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/data-segregation")
@RequiredArgsConstructor
@Tag(name = "Data Segregation Management", description = "Endpoints for hierarchical lead segregation matrix and deep analytics")
public class DataSegregationController {

    private final IDataSegregationService segregationService;

    @GetMapping("/capabilities")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DATA_SEGREGATION_FULL_FLOW_VIEW')")
    @Operation(summary = "Get resolved flow visibility capabilities for current authenticated user")
    public ResponseEntity<ApiResponse<DataSegregationCapabilitiesDTO>> getCapabilities() {
        DataSegregationCapabilitiesDTO response = segregationService.getCapabilities();
        return ResponseEntity.ok(ApiResponse.success("Data segregation capabilities retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/course-types")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DATA_SEGREGATION_FULL_FLOW_VIEW') or hasAuthority('DATA_SEGREGATION_COURSE_TYPE_VIEW')")
    @Operation(summary = "Get active course types with total lead counts within data scope")
    public ResponseEntity<ApiResponse<List<CourseTypeSegregationDTO>>> getCourseTypes()
            throws UnauthorizedException, BadRequestException {
        List<CourseTypeSegregationDTO> response = segregationService.getCourseTypesSummary();
        return ResponseEntity.ok(ApiResponse.success("Course types summary retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/matrix")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DATA_SEGREGATION_FULL_FLOW_VIEW')")
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
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_USER_ANALYTICS') or hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DATA_SEGREGATION_FULL_FLOW_VIEW')")
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
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_LEAD_STATUS_ANALYTICS') or hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DATA_SEGREGATION_FULL_FLOW_VIEW')")
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

    @GetMapping("/course-wise")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_COURSE_VIEW') or hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DATA_SEGREGATION_FULL_FLOW_VIEW')")
    @Operation(summary = "Get course-wise lead segregation for a selected course type / category")
    public ResponseEntity<ApiResponse<CourseSegregationResponseDTO>> getCourseWiseSegregation(
            @RequestParam(name = "courseTypeId") UUID courseTypeId,
            @RequestParam(name = "leadSourceId", required = false) UUID leadSourceId,
            @RequestParam(name = "boardId", required = false) UUID boardId,
            @RequestParam(name = "gradeId", required = false) UUID gradeId,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "sortBy", defaultValue = "total") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "desc") String sortDirection)
            throws UnauthorizedException, BadRequestException {
        CourseSegregationResponseDTO response = segregationService.getCourseWiseSegregation(courseTypeId, leadSourceId, boardId, gradeId, search, page, size, sortBy, sortDirection);
        return ResponseEntity.ok(ApiResponse.success("Course-wise segregation fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/courses/{courseId}/users")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_COURSE_USER_VIEW') or hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DATA_SEGREGATION_FULL_FLOW_VIEW')")
    @Operation(summary = "Get user-wise lead segregation and status counts for a selected course")
    public ResponseEntity<ApiResponse<CourseUserSegregationResponseDTO>> getCourseUserWiseSegregation(
            @PathVariable(name = "courseId") UUID courseId,
            @RequestParam(name = "leadSourceId", required = false) UUID leadSourceId,
            @RequestParam(name = "boardId", required = false) UUID boardId,
            @RequestParam(name = "gradeId", required = false) UUID gradeId,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "sortBy", defaultValue = "total") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "desc") String sortDirection)
            throws UnauthorizedException, BadRequestException {
        CourseUserSegregationResponseDTO response = segregationService.getCourseUserWiseSegregation(courseId, leadSourceId, boardId, gradeId, search, page, size, sortBy, sortDirection);
        return ResponseEntity.ok(ApiResponse.success("Course user-wise segregation fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/user-allocation-summary")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_USER_ALLOCATION_VIEW') or hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DATA_SEGREGATION_FULL_FLOW_VIEW')")
    @Operation(summary = "Get user allocation summary: total users with allotted data and users currently working")
    public ResponseEntity<ApiResponse<UserAllocationSummaryDTO>> getUserAllocationSummary(
            @Valid DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {
        UserAllocationSummaryDTO response = segregationService.getUserAllocationSummary(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("User allocation summary retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/user-allocation-users")
    @PreAuthorize("hasAuthority('DATA_SEGREGATION_USER_ALLOCATION_USERS_VIEW') or hasAuthority('DATA_SEGREGATION_VIEW') or hasAuthority('DATA_SEGREGATION_FULL_FLOW_VIEW')")
    @Operation(summary = "Get detailed list of users with allotted data and currently working status matching filters")
    public ResponseEntity<ApiResponse<UserAllocationUsersResponseDTO>> getUserAllocationUsers(
            @Valid DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {
        UserAllocationUsersResponseDTO response = segregationService.getUserAllocationUsers(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("User allocation users retrieved successfully", response, HttpStatus.OK.value()));
    }
}
