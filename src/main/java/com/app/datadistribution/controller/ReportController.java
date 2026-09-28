package com.app.datadistribution.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.report.AcademicSessionDTO;
import com.app.datadistribution.dto.report.ReportFilterRequest;
import com.app.datadistribution.dto.report.UserPerformanceReportResponse;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.IReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports Management", description = "Endpoints for User Performance & Departmental Reports with Dynamic RBAC and Scoping")
public class ReportController {

    private final IReportService reportService;

    @GetMapping("/user-performance")
    @PreAuthorize("hasAuthority('REPORT_VIEW') or hasAuthority('REPORT_SELF_VIEW') or hasAuthority('REPORT_DEPARTMENT_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get user performance & data-distribution report based on counselor, course, lead status, department, session, and date range")
    public ResponseEntity<ApiResponse<UserPerformanceReportResponse>> getUserPerformanceReport(
            @Valid @ModelAttribute ReportFilterRequest filterRequest)
            throws UnauthorizedException, BadRequestException {
        UserPerformanceReportResponse response = reportService.getUserPerformanceReport(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("User performance report retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/user-performance/export")
    @PreAuthorize("hasAuthority('REPORT_EXPORT') or hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Export user performance report to Excel (.xlsx)")
    public ResponseEntity<byte[]> exportUserPerformanceReport(
            @Valid @ModelAttribute ReportFilterRequest filterRequest)
            throws UnauthorizedException, BadRequestException {
        byte[] excelBytes = reportService.exportUserPerformanceReportToExcel(filterRequest);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "Performance_Report_" + System.currentTimeMillis() + ".xlsx");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('REPORT_VIEW') or hasAuthority('REPORT_SELF_VIEW') or hasAuthority('REPORT_DEPARTMENT_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get available academic sessions dynamically")
    public ResponseEntity<ApiResponse<List<AcademicSessionDTO>>> getAcademicSessions() {
        List<AcademicSessionDTO> sessions = reportService.getAcademicSessions();
        return ResponseEntity.ok(ApiResponse.success("Academic sessions retrieved successfully", sessions, HttpStatus.OK.value()));
    }

    @GetMapping("/active-session")
    @PreAuthorize("hasAuthority('REPORT_VIEW') or hasAuthority('REPORT_SELF_VIEW') or hasAuthority('REPORT_DEPARTMENT_VIEW') or hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Get current dynamically active academic session")
    public ResponseEntity<ApiResponse<AcademicSessionDTO>> getActiveSession() {
        AcademicSessionDTO active = reportService.getActiveSession();
        return ResponseEntity.ok(ApiResponse.success("Active academic session retrieved successfully", active, HttpStatus.OK.value()));
    }
}
