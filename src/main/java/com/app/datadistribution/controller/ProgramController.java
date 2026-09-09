package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.course.CourseResponseDTO;
import com.app.datadistribution.dto.program.ProgramCourseMappingRequestDTO;
import com.app.datadistribution.dto.program.ProgramPagedResponseDTO;
import com.app.datadistribution.dto.program.ProgramRequestDTO;
import com.app.datadistribution.dto.program.ProgramResponseDTO;
import com.app.datadistribution.dto.program.ProgramSummaryDTO;
import com.app.datadistribution.service.interfaces.IProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/programs")
@RequiredArgsConstructor
@Tag(name = "Program Management", description = "Endpoints for managing university programs/schools and course mappings")
public class ProgramController {

    private final IProgramService programService;

    @PostMapping
    @PreAuthorize("hasAuthority('PROGRAM_CREATE')")
    @Operation(summary = "Create a new program")
    public ResponseEntity<ApiResponse<ProgramResponseDTO>> create(@Valid @RequestBody ProgramRequestDTO request) {
        ProgramResponseDTO response = programService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Program created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROGRAM_UPDATE')")
    @Operation(summary = "Update an existing program")
    public ResponseEntity<ApiResponse<ProgramResponseDTO>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ProgramRequestDTO request) {
        ProgramResponseDTO response = programService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Program updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROGRAM_VIEW')")
    @Operation(summary = "Get program details by ID")
    public ResponseEntity<ApiResponse<ProgramResponseDTO>> getById(@PathVariable("id") UUID id) {
        ProgramResponseDTO response = programService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Program fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROGRAM_VIEW')")
    @Operation(summary = "Get list of programs with pagination, sorting, and search")
    public ResponseEntity<ApiResponse<ProgramPagedResponseDTO>> getAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        ProgramPagedResponseDTO response = programService.getAll(pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Programs retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('PROGRAM_VIEW')")
    @Operation(summary = "Get list of all active programs (summary)")
    public ResponseEntity<ApiResponse<List<ProgramSummaryDTO>>> getAllActive() {
        List<ProgramSummaryDTO> response = programService.getAllActive();
        return ResponseEntity.ok(ApiResponse.success("Active programs retrieved successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PROGRAM_DELETE')")
    @Operation(summary = "Soft delete a program")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") UUID id) {
        programService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Program deleted successfully", null, HttpStatus.OK.value()));
    }

    @PutMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('PROGRAM_UPDATE')")
    @Operation(summary = "Toggle program active/inactive status")
    public ResponseEntity<ApiResponse<ProgramResponseDTO>> toggleActive(@PathVariable("id") UUID id) {
        ProgramResponseDTO response = programService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Program status toggled successfully", response, HttpStatus.OK.value()));
    }

    @PutMapping("/{id}/courses")
    @PreAuthorize("hasAuthority('PROGRAM_UPDATE')")
    @Operation(summary = "Map courses to program")
    public ResponseEntity<ApiResponse<ProgramResponseDTO>> mapCourses(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ProgramCourseMappingRequestDTO request) {
        ProgramResponseDTO response = programService.mapCoursesToProgram(id, request);
        return ResponseEntity.ok(ApiResponse.success("Program courses mapped successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}/courses")
    @PreAuthorize("hasAuthority('PROGRAM_VIEW')")
    @Operation(summary = "Get active courses mapped to a program")
    public ResponseEntity<ApiResponse<List<CourseResponseDTO>>> getCoursesByProgramId(@PathVariable("id") UUID id) {
        List<CourseResponseDTO> response = programService.getCoursesByProgramId(id);
        return ResponseEntity.ok(ApiResponse.success("Program courses retrieved successfully", response, HttpStatus.OK.value()));
    }
}
