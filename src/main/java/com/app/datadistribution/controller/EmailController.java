package com.app.datadistribution.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.email.EmailConfigStatusDTO;
import com.app.datadistribution.dto.email.EmailLogResponseDTO;
import com.app.datadistribution.dto.email.EmailResponse;
import com.app.datadistribution.dto.email.SendEmailRequest;
import com.app.datadistribution.dto.email.TestEmailRequest;
import com.app.datadistribution.enums.EmailStatus;
import com.app.datadistribution.enums.EmailType;
import com.app.datadistribution.service.interfaces.IEmailService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
@Tag(name = "Email & Notification Management", description = "Endpoints for managing emails, audit delivery logs, and testing SMTP configuration")
public class EmailController {

    private final IEmailService emailService;

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('EMAIL_SEND') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Send a custom notification email")
    public ResponseEntity<ApiResponse<EmailResponse>> sendCustomEmail(
            @Valid @RequestBody SendEmailRequest request) {
        EmailResponse response = emailService.sendCustomEmail(request);
        return ResponseEntity.ok(ApiResponse.success("Email processed successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority('EMAIL_CONFIG_TEST') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Test SMTP provider connectivity and HTML email delivery")
    public ResponseEntity<ApiResponse<EmailResponse>> sendTestEmail(
            @Valid @RequestBody TestEmailRequest request) {
        EmailResponse response = emailService.sendTestEmail(request);
        return ResponseEntity.ok(ApiResponse.success("Test email processed", response, HttpStatus.OK.value()));
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('EMAIL_LOG_VIEW') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get paginated audit logs of email deliveries")
    public ResponseEntity<ApiResponse<Page<EmailLogResponseDTO>>> getEmailLogs(
            @RequestParam(value = "status", required = false) EmailStatus status,
            @RequestParam(value = "emailType", required = false) EmailType emailType,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection) {

        Sort sort = "ASC".equalsIgnoreCase(sortDirection)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<EmailLogResponseDTO> logs = emailService.getEmailLogs(status, emailType, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Email audit logs retrieved successfully", logs, HttpStatus.OK.value()));
    }

    @GetMapping("/config/status")
    @PreAuthorize("hasAuthority('EMAIL_CONFIG_VIEW') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get current email system configuration status (Sanitized, no passwords)")
    public ResponseEntity<ApiResponse<EmailConfigStatusDTO>> getEmailConfigStatus() {
        EmailConfigStatusDTO status = emailService.getConfigStatus();
        return ResponseEntity.ok(ApiResponse.success("Email configuration status retrieved successfully", status, HttpStatus.OK.value()));
    }
}
