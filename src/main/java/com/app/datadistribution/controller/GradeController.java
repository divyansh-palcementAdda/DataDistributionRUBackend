package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.GradePageResponse;
import com.app.datadistribution.dto.lead.GradeRequest;
import com.app.datadistribution.dto.lead.GradeResponse;
import com.app.datadistribution.service.interfaces.IGradeService;
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
@RequestMapping("/api/grades")
@RequiredArgsConstructor
@Tag(name = "Grade Management", description = "Endpoints for managing dynamic lead grades")
public class GradeController {

    private final IGradeService gradeService;

    @PostMapping
    @PreAuthorize("hasAuthority('GRADE_CREATE')")
    @Operation(summary = "Create a new grade")
    public ResponseEntity<ApiResponse<GradeResponse>> create(@Valid @RequestBody GradeRequest request) {
        GradeResponse response = gradeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Grade created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('GRADE_UPDATE')")
    @Operation(summary = "Update an existing grade")
    public ResponseEntity<ApiResponse<GradeResponse>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody GradeRequest request) {
        GradeResponse response = gradeService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Grade updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GRADE_VIEW')")
    @Operation(summary = "Get grade details by ID")
    public ResponseEntity<ApiResponse<GradeResponse>> getById(@PathVariable("id") UUID id) {
        GradeResponse response = gradeService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Grade fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GRADE_VIEW')")
    @Operation(summary = "Get list of grades with pagination, sorting, search, and status filtering")
    public ResponseEntity<ApiResponse<GradePageResponse>> getAll(
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

        GradePageResponse response = gradeService.getAll(pageRequest, status);
        return ResponseEntity.ok(ApiResponse.success("Grades retrieved successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('GRADE_DELETE')")
    @Operation(summary = "Soft delete a grade")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") UUID id) {
        gradeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Grade deleted successfully", null, HttpStatus.OK.value()));
    }

    @PutMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('GRADE_UPDATE')")
    @Operation(summary = "Toggle grade active/inactive status")
    public ResponseEntity<ApiResponse<GradeResponse>> toggleActive(@PathVariable("id") UUID id) {
        GradeResponse response = gradeService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Grade toggled successfully", response, HttpStatus.OK.value()));
    }
}
