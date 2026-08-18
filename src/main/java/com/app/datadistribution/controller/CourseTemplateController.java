package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.coursetemplate.CourseTemplateRequestDTO;
import com.app.datadistribution.dto.coursetemplate.CourseTemplateResponseDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.service.interfaces.ICourseTemplateService;
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
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Course Template Management", description = "Endpoints for managing Course Templates and Lead communication templates")
public class CourseTemplateController {

    private final ICourseTemplateService courseTemplateService;

    @PostMapping("/course-templates")
    @PreAuthorize("hasAuthority('COURSE_TEMPLATE_CREATE')")
    @Operation(summary = "Create a new course template")
    public ResponseEntity<ApiResponse<CourseTemplateResponseDTO>> create(@Valid @RequestBody CourseTemplateRequestDTO request) throws BadRequestException {
        CourseTemplateResponseDTO response = courseTemplateService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course template created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/course-templates/{id}")
    @PreAuthorize("hasAuthority('COURSE_TEMPLATE_UPDATE')")
    @Operation(summary = "Update an existing course template")
    public ResponseEntity<ApiResponse<CourseTemplateResponseDTO>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CourseTemplateRequestDTO request) throws BadRequestException {
        CourseTemplateResponseDTO response = courseTemplateService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Course template updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/course-templates/{id}")
    @PreAuthorize("hasAuthority('COURSE_TEMPLATE_VIEW')")
    @Operation(summary = "Get course template by ID")
    public ResponseEntity<ApiResponse<CourseTemplateResponseDTO>> getById(@PathVariable("id") UUID id) {
        CourseTemplateResponseDTO response = courseTemplateService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Course template retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/course-templates")
    @PreAuthorize("hasAuthority('COURSE_TEMPLATE_VIEW')")
    @Operation(summary = "Get all active course templates")
    public ResponseEntity<ApiResponse<List<CourseTemplateResponseDTO>>> getAll() {
        List<CourseTemplateResponseDTO> response = courseTemplateService.getAllTemplates();
        return ResponseEntity.ok(ApiResponse.success("All course templates retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/courses/{courseId}/templates")
    @PreAuthorize("hasAuthority('COURSE_TEMPLATE_VIEW')")
    @Operation(summary = "Get course templates for a specific course")
    public ResponseEntity<ApiResponse<List<CourseTemplateResponseDTO>>> getByCourseId(@PathVariable("courseId") UUID courseId) {
        List<CourseTemplateResponseDTO> response = courseTemplateService.getTemplatesByCourseId(courseId);
        return ResponseEntity.ok(ApiResponse.success("Course templates for course retrieved successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/course-templates/{id}")
    @PreAuthorize("hasAuthority('COURSE_TEMPLATE_DELETE')")
    @Operation(summary = "Soft delete a course template")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") UUID id) {
        courseTemplateService.deleteTemplate(id);
        return ResponseEntity.ok(ApiResponse.success("Course template deleted successfully", null, HttpStatus.OK.value()));
    }
}
