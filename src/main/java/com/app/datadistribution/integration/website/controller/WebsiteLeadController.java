package com.app.datadistribution.integration.website.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.integration.website.dto.WebsiteLeadRequestDTO;
import com.app.datadistribution.integration.website.dto.WebsiteLeadResponseDTO;
import com.app.datadistribution.integration.website.security.WebsiteApiKeyValidator;
import com.app.datadistribution.integration.website.security.WebsiteRateLimiter;
import com.app.datadistribution.integration.website.service.interfaces.IWebsiteLeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/integrations/website")
@RequiredArgsConstructor
@Tag(name = "Website Leads Integration", description = "Secure single endpoint for university website forms to submit inbound leads directly into DDS")
public class WebsiteLeadController {

    private final IWebsiteLeadService websiteLeadService;
    private final WebsiteApiKeyValidator apiKeyValidator;
    private final WebsiteRateLimiter rateLimiter;

    @PostMapping(value = "/leads", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Submit Website Lead",
        description = "Public integration endpoint protected by X-Website-API-Key header. Ingests leads from all university website forms (Campus Tour, Fees Inquiry, etc.).",
        parameters = {
            @Parameter(
                name = WebsiteApiKeyValidator.API_KEY_HEADER,
                in = ParameterIn.HEADER,
                required = true,
                description = "Dedicated website integration secret API key",
                schema = @Schema(type = "string")
            )
        }
    )
    public ResponseEntity<ApiResponse<WebsiteLeadResponseDTO>> submitLead(
            @RequestHeader(value = WebsiteApiKeyValidator.API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody WebsiteLeadRequestDTO request,
            HttpServletRequest servletRequest) throws BadRequestException, UnauthorizedException {

        String clientIp = extractClientIp(servletRequest);

        // 1. Rate Limiting Enforcement
        if (!rateLimiter.tryAcquire(clientIp)) {
            log.warn("Website integration rejected request from IP due to rate limit: ip={}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Rate limit exceeded. Please try again later.", HttpStatus.TOO_MANY_REQUESTS.value()));
        }

        // 2. Secret API Key Authentication (constant-time, fail-closed)
        apiKeyValidator.validate(apiKey);

        // 3. Process Lead Submission via Canonical Ingestion Service
        WebsiteLeadResponseDTO response = websiteLeadService.ingestLead(request, clientIp);

        // 4. Return appropriate status: 200 for idempotent duplicate, 201 for freshly created
        if ("EXISTING_SUBMISSION".equalsIgnoreCase(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success(response.getMessage(), response, HttpStatus.OK.value()));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response.getMessage(), response, HttpStatus.CREATED.value()));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "UNKNOWN";
    }
}
