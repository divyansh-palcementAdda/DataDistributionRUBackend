package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.lead.CourseLeadMatrixRowDTO;
import com.app.datadistribution.dto.lead.LeadMatrixResponseDTO;
import com.app.datadistribution.dto.lead.ProgramLeadMatrixRowDTO;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.IUserLeadMatrixService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "User Lead Matrix", description = "Endpoints for course-wise and program-wise lead status matrices for a specific user")
public class UserLeadMatrixController {

    private final IUserLeadMatrixService userLeadMatrixService;

    @GetMapping("/api/users/{userId}/lead-matrix/course")
    @PreAuthorize("hasAuthority('USER_COURSE_MATRIX_VIEW')")
    @Operation(summary = "Get course-wise lead status matrix for a specific user")
    public ResponseEntity<ApiResponse<LeadMatrixResponseDTO<CourseLeadMatrixRowDTO>>> getCourseMatrix(
            @PathVariable UUID userId) throws UnauthorizedException {
        LeadMatrixResponseDTO<CourseLeadMatrixRowDTO> response = userLeadMatrixService.getCourseMatrix(userId);
        return ResponseEntity.ok(ApiResponse.success("Course lead matrix fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/api/users/{userId}/lead-matrix/program")
    @PreAuthorize("hasAuthority('USER_PROGRAM_MATRIX_VIEW')")
    @Operation(summary = "Get program-wise lead status matrix for a specific user")
    public ResponseEntity<ApiResponse<LeadMatrixResponseDTO<ProgramLeadMatrixRowDTO>>> getProgramMatrix(
            @PathVariable UUID userId) throws UnauthorizedException {
        LeadMatrixResponseDTO<ProgramLeadMatrixRowDTO> response = userLeadMatrixService.getProgramMatrix(userId);
        return ResponseEntity.ok(ApiResponse.success("Program lead matrix fetched successfully", response, HttpStatus.OK.value()));
    }
}
