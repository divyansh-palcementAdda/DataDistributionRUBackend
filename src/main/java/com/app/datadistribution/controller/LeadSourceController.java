package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadSourcePageResponse;
import com.app.datadistribution.dto.lead.LeadSourceRequest;
import com.app.datadistribution.dto.lead.LeadSourceResponse;
import com.app.datadistribution.service.interfaces.ILeadSourceService;
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
@RequestMapping("/api/lead-sources")
@RequiredArgsConstructor
@Tag(name = "Lead Source Management", description = "Endpoints for managing dynamic lead sources")
public class LeadSourceController {

    private final ILeadSourceService leadSourceService;

    @PostMapping
    @PreAuthorize("hasAuthority('LEADSOURCE_CREATE')")
    @Operation(summary = "Create a new lead source")
    public ResponseEntity<ApiResponse<LeadSourceResponse>> create(@Valid @RequestBody LeadSourceRequest request) {
        LeadSourceResponse response = leadSourceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lead source created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('LEADSOURCE_UPDATE')")
    @Operation(summary = "Update an existing lead source")
    public ResponseEntity<ApiResponse<LeadSourceResponse>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LeadSourceRequest request) {
        LeadSourceResponse response = leadSourceService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Lead source updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEADSOURCE_READ')")
    @Operation(summary = "Get lead source details by ID")
    public ResponseEntity<ApiResponse<LeadSourceResponse>> getById(@PathVariable("id") UUID id) {
        LeadSourceResponse response = leadSourceService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lead source fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LEADSOURCE_READ')")
    @Operation(summary = "Get list of lead sources with pagination, sorting, and search filtering")
    public ResponseEntity<ApiResponse<LeadSourcePageResponse>> getAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        LeadSourcePageResponse response = leadSourceService.getAll(pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Lead sources retrieved successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LEADSOURCE_DELETE')")
    @Operation(summary = "Soft delete a lead source")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") UUID id) {
        leadSourceService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Lead source deleted successfully", null, HttpStatus.OK.value()));
    }

    @PutMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('LEADSOURCE_UPDATE')")
    @Operation(summary = "Toggle lead source active/inactive status")
    public ResponseEntity<ApiResponse<LeadSourceResponse>> toggleActive(@PathVariable("id") UUID id) {
        LeadSourceResponse response = leadSourceService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Lead source status toggled successfully", response, HttpStatus.OK.value()));
    }
}
