package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.stream.StreamPageResponse;
import com.app.datadistribution.dto.stream.StreamRequest;
import com.app.datadistribution.dto.stream.StreamResponse;
import com.app.datadistribution.service.interfaces.IStreamService;
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
@RequestMapping("/api/streams")
@RequiredArgsConstructor
@Tag(name = "Stream Management", description = "Endpoints for managing dynamic lead streams")
public class StreamController {

    private final IStreamService streamService;

    @PostMapping
    @PreAuthorize("hasAuthority('STREAM_CREATE')")
    @Operation(summary = "Create a new stream")
    public ResponseEntity<ApiResponse<StreamResponse>> create(@Valid @RequestBody StreamRequest request) {
        StreamResponse response = streamService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stream created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STREAM_UPDATE')")
    @Operation(summary = "Update an existing stream")
    public ResponseEntity<ApiResponse<StreamResponse>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody StreamRequest request) {
        StreamResponse response = streamService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Stream updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STREAM_VIEW')")
    @Operation(summary = "Get stream details by ID")
    public ResponseEntity<ApiResponse<StreamResponse>> getById(@PathVariable("id") UUID id) {
        StreamResponse response = streamService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Stream fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STREAM_VIEW')")
    @Operation(summary = "Get list of streams with pagination, sorting, search, and status filtering")
    public ResponseEntity<ApiResponse<StreamPageResponse>> getAll(
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

        StreamPageResponse response = streamService.getAll(pageRequest, status);
        return ResponseEntity.ok(ApiResponse.success("Streams retrieved successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('STREAM_DELETE')")
    @Operation(summary = "Soft delete a stream")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") UUID id) {
        streamService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Stream deleted successfully", null, HttpStatus.OK.value()));
    }

    @PutMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('STREAM_UPDATE')")
    @Operation(summary = "Toggle stream active/inactive status")
    public ResponseEntity<ApiResponse<StreamResponse>> toggleActive(@PathVariable("id") UUID id) {
        StreamResponse response = streamService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Stream toggled successfully", response, HttpStatus.OK.value()));
    }
}
