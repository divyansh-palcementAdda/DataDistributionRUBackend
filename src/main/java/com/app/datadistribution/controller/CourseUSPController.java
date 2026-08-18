package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.courseusp.CourseUSPDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.service.interfaces.ICourseUSPService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Course USP Management", description = "Endpoints for managing dynamic USPs for a Course")
public class CourseUSPController {

    private final ICourseUSPService courseUSPService;

    @PostMapping("/courses/{courseId}/usps")
    @PreAuthorize("hasAuthority('COURSE_USP_CREATE') or hasAuthority('COURSE_UPDATE')")
    @Operation(summary = "Add a USP to a course")
    public ResponseEntity<ApiResponse<CourseUSPDTO>> createUSP(
            @PathVariable("courseId") UUID courseId,
            @RequestParam("content") String content,
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder,
            @RequestParam(value = "active", required = false) Boolean active) throws BadRequestException {
        CourseUSPDTO response = courseUSPService.createUSP(courseId, content, displayOrder, active);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course USP created successfully", response, HttpStatus.CREATED.value()));
    }

    @GetMapping("/courses/{courseId}/usps")
    @PreAuthorize("hasAuthority('COURSE_USP_VIEW') or hasAuthority('COURSE_VIEW')")
    @Operation(summary = "Get USPs for a course")
    public ResponseEntity<ApiResponse<List<CourseUSPDTO>>> getUSPs(
            @PathVariable("courseId") UUID courseId,
            @RequestParam(value = "activeOnly", defaultValue = "false") boolean activeOnly) {
        List<CourseUSPDTO> response = courseUSPService.getUSPsByCourseId(courseId, activeOnly);
        return ResponseEntity.ok(ApiResponse.success("Course USPs retrieved successfully", response, HttpStatus.OK.value()));
    }

    @PutMapping("/course-usps/{uspId}")
    @PreAuthorize("hasAuthority('COURSE_USP_UPDATE') or hasAuthority('COURSE_UPDATE')")
    @Operation(summary = "Update a course USP")
    public ResponseEntity<ApiResponse<CourseUSPDTO>> updateUSP(
            @PathVariable("uspId") UUID uspId,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder,
            @RequestParam(value = "active", required = false) Boolean active) {
        CourseUSPDTO response = courseUSPService.updateUSP(uspId, content, displayOrder, active);
        return ResponseEntity.ok(ApiResponse.success("Course USP updated successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/course-usps/{uspId}")
    @PreAuthorize("hasAuthority('COURSE_USP_DELETE') or hasAuthority('COURSE_UPDATE')")
    @Operation(summary = "Delete a course USP")
    public ResponseEntity<ApiResponse<Void>> deleteUSP(@PathVariable("uspId") UUID uspId) {
        courseUSPService.deleteUSP(uspId);
        return ResponseEntity.ok(ApiResponse.success("Course USP deleted successfully", null, HttpStatus.OK.value()));
    }
}
