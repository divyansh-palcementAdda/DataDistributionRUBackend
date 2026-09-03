package com.app.datadistribution.integration.cms.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.integration.cms.dto.StudentVerificationRequest;
import com.app.datadistribution.integration.cms.dto.StudentVerificationResponse;
import com.app.datadistribution.integration.cms.service.IStudentVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration/student")
@RequiredArgsConstructor
@Tag(name = "CMS Student Integration", description = "Endpoints for CMS student verification and matching")
public class StudentVerificationController {

    private final IStudentVerificationService studentVerificationService;

    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('LEAD_READ') or hasAuthority('LEAD_UPDATE') or hasAuthority('LEAD_STATUS_CHANGE')")
    @Operation(summary = "Verify student details directly against CMS")
    public ResponseEntity<ApiResponse<StudentVerificationResponse>> verifyStudent(
            @RequestBody StudentVerificationRequest request) {
        StudentVerificationResponse response = studentVerificationService.verifyStudent(request);
        return ResponseEntity.ok(ApiResponse.success("Student verification processed", response, HttpStatus.OK.value()));
    }
}
