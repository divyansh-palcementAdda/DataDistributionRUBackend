package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.*;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
@Tag(name = "Lead Management", description = "Endpoints for managing customer leads and sales pipeline")
public class LeadController {

    private final ILeadService leadService;
    private final ILeadFeedbackService leadFeedbackService;
    private final ILeadFollowUpService leadFollowUpService;
    private final ILeadAssignmentService leadAssignmentService;

    @PostMapping
    @PreAuthorize("hasAuthority('LEAD_CREATE')")
    @Operation(summary = "Create a new lead")
    public ResponseEntity<ApiResponse<LeadResponse>> create(@Valid @RequestBody LeadRequest request) throws BadRequestException, UnauthorizedException {
        LeadResponse response = leadService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lead created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_UPDATE')")
    @Operation(summary = "Update an existing lead")
    public ResponseEntity<ApiResponse<LeadResponse>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LeadRequest request) {
        LeadResponse response = leadService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Lead updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    @Operation(summary = "Get lead details by ID")
    public ResponseEntity<ApiResponse<LeadResponse>> getById(@PathVariable("id") UUID id) {
        LeadResponse response = leadService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lead fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LEAD_READ')")
    @Operation(summary = "Get list of leads with pagination, sorting, search, and source-wise filtering")
    public ResponseEntity<ApiResponse<LeadPageResponse>> getAllLeads(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sourceId", required = false) UUID sourceId) {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        LeadPageResponse response = leadService.getAllLeads(pageRequest, sourceId);
        return ResponseEntity.ok(ApiResponse.success("Leads retrieved successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_DELETE')")
    @Operation(summary = "Soft delete a lead")
    public ResponseEntity<ApiResponse<Void>> deleteLead(@PathVariable("id") UUID id) {
        leadService.deleteLead(id);
        return ResponseEntity.ok(ApiResponse.success("Lead deleted successfully", null, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/change-status")
    @PreAuthorize("hasAuthority('LEAD_STATUS_CHANGE')")
    @Operation(summary = "Change status of a lead (feedback is mandatory)")
    public ResponseEntity<ApiResponse<LeadResponse>> changeStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LeadStatusChangeRequest request) throws BadRequestException, UnauthorizedException {
        LeadResponse response = leadService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Lead status changed successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/feedback")
    @PreAuthorize("hasAuthority('LEAD_FEEDBACK_CREATE')")
    @Operation(summary = "Add feedback to a lead")
    public ResponseEntity<ApiResponse<LeadFeedbackResponse>> addFeedback(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LeadFeedbackRequest request) throws UnauthorizedException {
        LeadFeedbackResponse response = leadFeedbackService.addFeedback(id, request);
        return ResponseEntity.ok(ApiResponse.success("Feedback added successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}/feedbacks")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    @Operation(summary = "Get feedback history for a lead")
    public ResponseEntity<ApiResponse<List<LeadFeedbackResponse>>> getFeedbacks(@PathVariable("id") UUID id) {
        List<LeadFeedbackResponse> response = leadFeedbackService.getFeedbacksByLeadId(id);
        return ResponseEntity.ok(ApiResponse.success("Lead feedbacks fetched successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/followups")
    @PreAuthorize("hasAuthority('LEAD_FOLLOWUP_CREATE')")
    @Operation(summary = "Schedule a follow-up for a lead")
    public ResponseEntity<ApiResponse<LeadFollowUpResponse>> createFollowUp(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LeadFollowUpRequest request) throws UnauthorizedException {
        LeadFollowUpResponse response = leadFollowUpService.createFollowUp(id, request);
        return ResponseEntity.ok(ApiResponse.success("Follow-up scheduled successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}/followups")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    @Operation(summary = "Get follow-ups for a lead")
    public ResponseEntity<ApiResponse<List<LeadFollowUpResponse>>> getFollowUps(@PathVariable("id") UUID id) {
        List<LeadFollowUpResponse> response = leadFollowUpService.getFollowUpsByLeadId(id);
        return ResponseEntity.ok(ApiResponse.success("Lead follow-ups fetched successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/followups/{followUpId}/complete")
    @PreAuthorize("hasAuthority('LEAD_FOLLOWUP_CREATE')")
    @Operation(summary = "Mark a follow-up as completed")
    public ResponseEntity<ApiResponse<LeadFollowUpResponse>> completeFollowUp(
            @PathVariable("followUpId") UUID followUpId,
            @RequestParam(value = "remarks", required = false) String remarks) {
        LeadFollowUpResponse response = leadFollowUpService.completeFollowUp(followUpId, remarks);
        return ResponseEntity.ok(ApiResponse.success("Follow-up marked completed successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('LEAD_ASSIGN')")
    @Operation(summary = "Assign/reassign a lead to a user")
    public ResponseEntity<ApiResponse<LeadResponse>> assignLead(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LeadAssignmentRequest request) throws UnauthorizedException {
        LeadResponse response = leadAssignmentService.assignLead(id, request);
        return ResponseEntity.ok(ApiResponse.success("Lead assigned successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}/assignment-history")
    @PreAuthorize("hasAuthority('LEAD_HISTORY_READ')")
    @Operation(summary = "Get assignment history for a lead")
    public ResponseEntity<ApiResponse<List<LeadAssignmentHistoryResponse>>> getAssignmentHistory(@PathVariable("id") UUID id) {
        List<LeadAssignmentHistoryResponse> response = leadAssignmentService.getAssignmentHistoryByLeadId(id);
        return ResponseEntity.ok(ApiResponse.success("Lead assignment history fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAuthority('LEAD_HISTORY_READ')")
    @Operation(summary = "Get status history for a lead")
    public ResponseEntity<ApiResponse<List<LeadStatusHistoryResponse>>> getStatusHistory(@PathVariable("id") UUID id) {
        List<LeadStatusHistoryResponse> response = leadService.getStatusHistoryByLeadId(id);
        return ResponseEntity.ok(ApiResponse.success("Lead status history fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/stats/source-wise")
    @PreAuthorize("hasAuthority('LEAD_SOURCE_STATS')")
    @Operation(summary = "Get lead stats source-wise")
    public ResponseEntity<ApiResponse<List<LeadSourceStatsResponse>>> getSourceWiseStats() {
        List<LeadSourceStatsResponse> response = leadService.getSourceWiseStats();
        return ResponseEntity.ok(ApiResponse.success("Source-wise lead statistics retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/stats/status-wise")
    @PreAuthorize("hasAuthority('LEAD_SOURCE_STATS')")
    @Operation(summary = "Get lead stats status-wise")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStatusWiseStats() {
        Map<String, Long> response = leadService.getStatusWiseStats();
        return ResponseEntity.ok(ApiResponse.success("Status-wise lead statistics retrieved successfully", response, HttpStatus.OK.value()));
    }
}
