package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.common.PageResponseDTO;
import com.app.datadistribution.dto.course.CourseTypeRequestDTO;
import com.app.datadistribution.dto.course.CourseTypeResponseDTO;
import com.app.datadistribution.service.interfaces.ICourseTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/course-types")
@RequiredArgsConstructor
@Tag(name = "Course Type Management", description = "Endpoints for managing course categories and types")
public class CourseTypeController {

    private final ICourseTypeService courseTypeService;

    @PostMapping
    @PreAuthorize("hasAuthority('COURSE_TYPE_CREATE')")
    @Operation(summary = "Create a new course type")
    public ResponseEntity<ApiResponse<CourseTypeResponseDTO>> create(@Valid @RequestBody CourseTypeRequestDTO request) {
        CourseTypeResponseDTO response = courseTypeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course type created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COURSE_TYPE_UPDATE')")
    @Operation(summary = "Update an existing course type")
    public ResponseEntity<ApiResponse<CourseTypeResponseDTO>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CourseTypeRequestDTO request) {
        CourseTypeResponseDTO response = courseTypeService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Course type updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COURSE_TYPE_VIEW')")
    @Operation(summary = "Get course type details by ID")
    public ResponseEntity<ApiResponse<CourseTypeResponseDTO>> getById(@PathVariable("id") UUID id) {
        CourseTypeResponseDTO response = courseTypeService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Course type fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COURSE_TYPE_VIEW')")
    @Operation(summary = "Get list of course types with pagination, sorting, and search filtering")
    public ResponseEntity<ApiResponse<PageResponseDTO<CourseTypeResponseDTO>>> getAll(
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

        PageResponseDTO<CourseTypeResponseDTO> response = courseTypeService.getAll(pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Course types retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('COURSE_TYPE_VIEW')")
    @Operation(summary = "Get list of all active course types (for dropdown lists)")
    public ResponseEntity<ApiResponse<List<CourseTypeResponseDTO>>> getAllActive() {
        List<CourseTypeResponseDTO> response = courseTypeService.getAllActive();
        return ResponseEntity.ok(ApiResponse.success("Active course types retrieved successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COURSE_TYPE_DELETE')")
    @Operation(summary = "Soft delete a course type")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") UUID id) {
        courseTypeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Course type deleted successfully", null, HttpStatus.OK.value()));
    }

    @PutMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('COURSE_TYPE_UPDATE')")
    @Operation(summary = "Toggle course type active/inactive status")
    public ResponseEntity<ApiResponse<CourseTypeResponseDTO>> toggleActive(@PathVariable("id") UUID id) {
        CourseTypeResponseDTO response = courseTypeService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Course type status toggled successfully", response, HttpStatus.OK.value()));
    }
}
