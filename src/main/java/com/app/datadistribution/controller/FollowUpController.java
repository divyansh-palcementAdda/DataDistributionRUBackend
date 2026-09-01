package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.followup.FollowUpPagedResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpSummaryDTO;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.FollowUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.datadistribution.dto.lead.CancelFollowUpRequest;
import com.app.datadistribution.dto.lead.CompleteFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.service.interfaces.ILeadFollowUpService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/followups")
@RequiredArgsConstructor
@Tag(name = "Follow-Up Management", description = "Centralized endpoints for follow-up dashboard and activities")
public class FollowUpController {

    private final FollowUpService followUpService;
    private final ILeadFollowUpService leadFollowUpService;
    private final com.app.datadistribution.service.interfaces.IDropdownService dropdownService;

    @GetMapping("/statuses/dropdown")
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW') or hasAuthority('DROPDOWN_STATUS_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get valid Follow-Up lifecycle states dropdown")
    public ResponseEntity<ApiResponse<java.util.List<com.app.datadistribution.dto.dropdown.FollowUpStatusDropdownResponse>>> getFollowUpStatusesDropdown() {
        java.util.List<com.app.datadistribution.dto.dropdown.FollowUpStatusDropdownResponse> response = dropdownService.getFollowUpStatusesDropdown();
        return ResponseEntity.ok(ApiResponse.success("Follow-up statuses dropdown retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/dashboard/status-counts")
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW') or hasAuthority('DASHBOARD_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get current lead counts for follow-up statuses scoped by user permissions")
    public ResponseEntity<ApiResponse<java.util.List<com.app.datadistribution.dto.followup.FollowUpStatusCountDTO>>> getFollowUpStatusCounts() throws UnauthorizedException, BadRequestException {
        java.util.List<com.app.datadistribution.dto.followup.FollowUpStatusCountDTO> response = followUpService.getFollowUpStatusCounts();
        return ResponseEntity.ok(ApiResponse.success("Follow-up status counts retrieved successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEAD_FOLLOWUP_CREATE') or hasAuthority('FOLLOWUP_CREATE')")
    @Operation(summary = "Schedule a follow-up for a lead")
    public ResponseEntity<ApiResponse<LeadFollowUpResponse>> scheduleFollowUp(
            @Valid @RequestBody LeadFollowUpRequest request) throws UnauthorizedException, BadRequestException {
        LeadFollowUpResponse response = leadFollowUpService.createFollowUp(request);
        return ResponseEntity.ok(ApiResponse.success("Follow-up scheduled successfully", response, HttpStatus.OK.value()));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('LEAD_FOLLOWUP_CREATE') or hasAuthority('FOLLOWUP_UPDATE')")
    @Operation(summary = "Mark a follow-up as completed (PATCH)")
    public ResponseEntity<ApiResponse<LeadFollowUpResponse>> completeFollowUpPatch(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) CompleteFollowUpRequest request,
            @RequestParam(value = "remarks", required = false) String paramRemarks) throws UnauthorizedException, BadRequestException {
        String feedback = (request != null && request.getRemarks() != null) ? request.getRemarks() : paramRemarks;
        LeadFollowUpResponse response = leadFollowUpService.completeFollowUp(id, feedback);
        return ResponseEntity.ok(ApiResponse.success("Follow-up marked completed successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('LEAD_FOLLOWUP_CREATE') or hasAuthority('FOLLOWUP_UPDATE')")
    @Operation(summary = "Mark a follow-up as completed (POST)")
    public ResponseEntity<ApiResponse<LeadFollowUpResponse>> completeFollowUpPost(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) CompleteFollowUpRequest request,
            @RequestParam(value = "remarks", required = false) String paramRemarks) throws UnauthorizedException, BadRequestException {
        String feedback = (request != null && request.getRemarks() != null) ? request.getRemarks() : paramRemarks;
        LeadFollowUpResponse response = leadFollowUpService.completeFollowUp(id, feedback);
        return ResponseEntity.ok(ApiResponse.success("Follow-up marked completed successfully", response, HttpStatus.OK.value()));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('LEAD_FOLLOWUP_CREATE') or hasAuthority('FOLLOWUP_UPDATE')")
    @Operation(summary = "Mark a follow-up as cancelled (PATCH)")
    public ResponseEntity<ApiResponse<LeadFollowUpResponse>> cancelFollowUpPatch(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) CancelFollowUpRequest request,
            @RequestParam(value = "remarks", required = false) String paramRemarks) throws UnauthorizedException, BadRequestException {
        String feedback = (request != null && request.getRemarks() != null) ? request.getRemarks() : paramRemarks;
        LeadFollowUpResponse response = leadFollowUpService.cancelFollowUp(id, feedback);
        return ResponseEntity.ok(ApiResponse.success("Follow-up marked cancelled successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('LEAD_FOLLOWUP_CREATE') or hasAuthority('FOLLOWUP_UPDATE')")
    @Operation(summary = "Mark a follow-up as cancelled (POST)")
    public ResponseEntity<ApiResponse<LeadFollowUpResponse>> cancelFollowUpPost(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) CancelFollowUpRequest request,
            @RequestParam(value = "remarks", required = false) String paramRemarks) throws UnauthorizedException, BadRequestException {
        String feedback = (request != null && request.getRemarks() != null) ? request.getRemarks() : paramRemarks;
        LeadFollowUpResponse response = leadFollowUpService.cancelFollowUp(id, feedback);
        return ResponseEntity.ok(ApiResponse.success("Follow-up marked cancelled successfully", response, HttpStatus.OK.value()));
    }

    @PatchMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('LEAD_FOLLOWUP_CREATE') or hasAuthority('FOLLOWUP_UPDATE')")
    @Operation(summary = "Reschedule a follow-up (PATCH)")
    public ResponseEntity<ApiResponse<LeadFollowUpResponse>> rescheduleFollowUpPatch(
            @PathVariable("id") UUID id,
            @Valid @RequestBody com.app.datadistribution.dto.lead.RescheduleFollowUpRequest request) throws UnauthorizedException, BadRequestException {
        LeadFollowUpResponse response = leadFollowUpService.rescheduleFollowUp(id, request);
        return ResponseEntity.ok(ApiResponse.success("Follow-up rescheduled successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('LEAD_FOLLOWUP_CREATE') or hasAuthority('FOLLOWUP_UPDATE')")
    @Operation(summary = "Reschedule a follow-up (POST)")
    public ResponseEntity<ApiResponse<LeadFollowUpResponse>> rescheduleFollowUpPost(
            @PathVariable("id") UUID id,
            @Valid @RequestBody com.app.datadistribution.dto.lead.RescheduleFollowUpRequest request) throws UnauthorizedException, BadRequestException {
        LeadFollowUpResponse response = leadFollowUpService.rescheduleFollowUp(id, request);
        return ResponseEntity.ok(ApiResponse.success("Follow-up rescheduled successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW')")
    @Operation(summary = "Get all follow-ups with search, pagination, and dynamic filtering")
    public ResponseEntity<ApiResponse<FollowUpPagedResponseDTO>> getAllFollowUps(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "followUpDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "status", required = false) FollowUpStatus status,
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "leadId", required = false) UUID leadId,
            @RequestParam(value = "leadStatusId", required = false) UUID leadStatusId,
            @RequestParam(value = "leadStatusIds", required = false) List<UUID> leadStatusIds) throws UnauthorizedException, BadRequestException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FollowUpPagedResponseDTO response = followUpService.getAllFollowUps(pageRequest, date, status, userId, leadId, leadStatusId, leadStatusIds);
        return ResponseEntity.ok(ApiResponse.success("Follow-ups retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW')")
    @Operation(summary = "Get follow-ups for a specific user")
    public ResponseEntity<ApiResponse<FollowUpPagedResponseDTO>> getFollowUpsByUser(
            @PathVariable("userId") UUID userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "followUpDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws UnauthorizedException, BadRequestException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FollowUpPagedResponseDTO response = followUpService.getFollowUpsByUserId(userId, pageRequest);
        return ResponseEntity.ok(ApiResponse.success("User follow-ups retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/today")
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW')")
    @Operation(summary = "Get today's follow-ups")
    public ResponseEntity<ApiResponse<FollowUpPagedResponseDTO>> getTodayFollowUps(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "followUpDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws UnauthorizedException, BadRequestException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FollowUpPagedResponseDTO response = followUpService.getTodayFollowUps(pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Today's follow-ups retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW')")
    @Operation(summary = "Get pending follow-ups")
    public ResponseEntity<ApiResponse<FollowUpPagedResponseDTO>> getPendingFollowUps(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "followUpDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws UnauthorizedException, BadRequestException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FollowUpPagedResponseDTO response = followUpService.getPendingFollowUps(pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Pending follow-ups retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/completed")
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW')")
    @Operation(summary = "Get completed follow-ups")
    public ResponseEntity<ApiResponse<FollowUpPagedResponseDTO>> getCompletedFollowUps(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "followUpDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws UnauthorizedException, BadRequestException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FollowUpPagedResponseDTO response = followUpService.getCompletedFollowUps(pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Completed follow-ups retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW')")
    @Operation(summary = "Get follow-up dashboard statistics")
    public ResponseEntity<ApiResponse<FollowUpSummaryDTO>> getFollowUpDashboardStats() throws UnauthorizedException, BadRequestException {
        FollowUpSummaryDTO response = followUpService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success("Follow-up dashboard statistics retrieved successfully", response, HttpStatus.OK.value()));
    }
}
