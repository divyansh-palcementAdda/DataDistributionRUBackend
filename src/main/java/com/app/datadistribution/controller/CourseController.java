package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.course.CoursePagedResponseDTO;
import com.app.datadistribution.dto.course.CourseRequestDTO;
import com.app.datadistribution.dto.course.CourseResponseDTO;
import com.app.datadistribution.service.interfaces.ICourseService;
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

import com.app.datadistribution.dto.course.CourseBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.course.CourseBulkUploadResponseDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.service.interfaces.ICourseBulkUploadService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Tag(name = "Course Management", description = "Endpoints for managing university courses and curriculums")
public class CourseController {

    private final ICourseService courseService;
    private final ICourseBulkUploadService bulkUploadService;

    @PostMapping
    @PreAuthorize("hasAuthority('COURSE_CREATE')")
    @Operation(summary = "Create a new course")
    public ResponseEntity<ApiResponse<CourseResponseDTO>> create(@Valid @RequestBody CourseRequestDTO request) {
        CourseResponseDTO response = courseService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COURSE_UPDATE')")
    @Operation(summary = "Update an existing course")
    public ResponseEntity<ApiResponse<CourseResponseDTO>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CourseRequestDTO request) {
        CourseResponseDTO response = courseService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Course updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COURSE_VIEW')")
    @Operation(summary = "Get course details by ID")
    public ResponseEntity<ApiResponse<CourseResponseDTO>> getById(@PathVariable("id") UUID id) {
        CourseResponseDTO response = courseService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Course fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COURSE_VIEW')")
    @Operation(summary = "Get list of courses with pagination, sorting, search, and type-wise filtering")
    public ResponseEntity<ApiResponse<CoursePagedResponseDTO>> getAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "courseTypeId", required = false) UUID courseTypeId) {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        CoursePagedResponseDTO response = courseService.getAll(pageRequest, courseTypeId);
        return ResponseEntity.ok(ApiResponse.success("Courses retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('COURSE_VIEW')")
    @Operation(summary = "Get list of all active courses (for dropdown lists)")
    public ResponseEntity<ApiResponse<List<CourseResponseDTO>>> getAllActive() {
        List<CourseResponseDTO> response = courseService.getAllActive();
        return ResponseEntity.ok(ApiResponse.success("Active courses retrieved successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COURSE_DELETE')")
    @Operation(summary = "Soft delete a course")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") UUID id) {
        courseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Course deleted successfully", null, HttpStatus.OK.value()));
    }

    @PutMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('COURSE_UPDATE')")
    @Operation(summary = "Toggle course active/inactive status")
    public ResponseEntity<ApiResponse<CourseResponseDTO>> toggleActive(@PathVariable("id") UUID id) {
        CourseResponseDTO response = courseService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Course status toggled successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('COURSE_BULK_UPLOAD') or hasAuthority('COURSE_CREATE') or hasAuthority('COURSE_UPDATE')")
    @Operation(summary = "Bulk upload and update university courses from Excel")
    public ResponseEntity<ApiResponse<CourseBulkUploadResponseDTO>> bulkUpload(
            @RequestParam("file") MultipartFile file) throws BadRequestException {
        CourseBulkUploadResponseDTO response = bulkUploadService.bulkUpload(file);
        return ResponseEntity.ok(ApiResponse.success("Course bulk upload completed successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping(value = "/bulk-upload/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('COURSE_BULK_UPLOAD') or hasAuthority('COURSE_CREATE') or hasAuthority('COURSE_UPDATE')")
    @Operation(summary = "Validate and preview Course Excel import without committing changes")
    public ResponseEntity<ApiResponse<CourseBulkUploadPreviewResponseDTO>> validateBulkUpload(
            @RequestParam("file") MultipartFile file) throws BadRequestException {
        CourseBulkUploadPreviewResponseDTO response = bulkUploadService.validateExcel(file);
        return ResponseEntity.ok(ApiResponse.success("Course Excel validation preview generated", response, HttpStatus.OK.value()));
    }

    @GetMapping("/bulk-upload/template")
    @PreAuthorize("hasAuthority('COURSE_BULK_UPLOAD_TEMPLATE_DOWNLOAD') or hasAuthority('COURSE_BULK_UPLOAD') or hasAuthority('COURSE_CREATE') or hasAuthority('COURSE_VIEW')")
    @Operation(summary = "Download official Course bulk upload Excel template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] excelBytes = bulkUploadService.generateTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("course_bulk_upload_template.xlsx").build());
        headers.setContentLength(excelBytes.length);
        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/bulk-upload/{importId}/error-file")
    @PreAuthorize("hasAuthority('COURSE_BULK_UPLOAD') or hasAuthority('COURSE_CREATE') or hasAuthority('COURSE_VIEW')")
    @Operation(summary = "Download error sheet for an executed or validated course bulk upload")
    public ResponseEntity<byte[]> downloadErrorFile(@PathVariable("importId") UUID importId) {
        byte[] errorFileBytes = bulkUploadService.getErrorFile(importId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("course_bulk_upload_errors_" + importId + ".xlsx").build());
        headers.setContentLength(errorFileBytes.length);
        return new ResponseEntity<>(errorFileBytes, headers, HttpStatus.OK);
    }
}
