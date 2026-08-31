package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadStatusPageResponse;
import com.app.datadistribution.dto.lead.LeadStatusRequest;
import com.app.datadistribution.dto.lead.LeadStatusResponse;
import com.app.datadistribution.service.interfaces.ILeadStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lead-statuses")
@RequiredArgsConstructor
@Tag(name = "Lead Status Management", description = "Endpoints for managing dynamic lead pipeline statuses")
public class LeadStatusController {

    private final ILeadStatusService leadStatusService;
    private final com.app.datadistribution.service.interfaces.IDropdownService dropdownService;

    @GetMapping("/follow-up/dropdown")
    @PreAuthorize("hasAuthority('LEAD_STATUS_VIEW') or hasAuthority('DROPDOWN_STATUS_VIEW') or hasAuthority('FOLLOWUP_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get lead statuses configured as follow-up statuses for scheduling")
    public ResponseEntity<ApiResponse<java.util.List<com.app.datadistribution.dto.dropdown.LeadStatusDropdownResponse>>> getFollowUpLeadStatusesDropdown() {
        java.util.List<com.app.datadistribution.dto.dropdown.LeadStatusDropdownResponse> response = dropdownService.getFollowUpLeadStatusesDropdown();
        return ResponseEntity.ok(ApiResponse.success("Follow-up lead statuses dropdown retrieved successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEAD_STATUS_CREATE')")
    @Operation(summary = "Create a new lead status")
    public ResponseEntity<ApiResponse<LeadStatusResponse>> create(@Valid @RequestBody LeadStatusRequest request) {
        LeadStatusResponse response = leadStatusService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lead status created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_STATUS_UPDATE')")
    @Operation(summary = "Update an existing lead status")
    public ResponseEntity<ApiResponse<LeadStatusResponse>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LeadStatusRequest request) {
        LeadStatusResponse response = leadStatusService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Lead status updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_STATUS_VIEW')")
    @Operation(summary = "Get lead status details by ID")
    public ResponseEntity<ApiResponse<LeadStatusResponse>> getById(@PathVariable("id") UUID id) {
        LeadStatusResponse response = leadStatusService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lead status fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LEAD_STATUS_VIEW')")
    @Operation(summary = "Get list of lead statuses with pagination, sorting, search, and status filtering")
    public ResponseEntity<ApiResponse<LeadStatusPageResponse>> getAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "displayOrder") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status) {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        LeadStatusPageResponse response = leadStatusService.getAll(pageRequest, status);
        return ResponseEntity.ok(ApiResponse.success("Lead statuses retrieved successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_STATUS_DELETE')")
    @Operation(summary = "Soft delete a lead status")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") UUID id) {
        leadStatusService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Lead status deleted successfully", null, HttpStatus.OK.value()));
    }

    @PutMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('LEAD_STATUS_UPDATE')")
    @Operation(summary = "Toggle lead status active/inactive status")
    public ResponseEntity<ApiResponse<LeadStatusResponse>> toggleActive(@PathVariable("id") UUID id) {
        LeadStatusResponse response = leadStatusService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Lead status toggled successfully", response, HttpStatus.OK.value()));
    }
}
