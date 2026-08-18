package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.courseimage.CourseImageDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.service.interfaces.ICourseImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Course Image Management", description = "Endpoints for managing multiple images for a Course")
public class CourseImageController {

    private final ICourseImageService courseImageService;

    @PostMapping(value = "/courses/{courseId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('COURSE_IMAGE_UPLOAD') or hasAuthority('COURSE_UPDATE')")
    @Operation(summary = "Upload image for a course")
    public ResponseEntity<ApiResponse<CourseImageDTO>> uploadImage(
            @PathVariable("courseId") UUID courseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder) throws BadRequestException {
        CourseImageDTO response = courseImageService.uploadImage(courseId, file, displayName, displayOrder);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course image uploaded successfully", response, HttpStatus.CREATED.value()));
    }

    @GetMapping("/courses/{courseId}/images")
    @PreAuthorize("hasAuthority('COURSE_IMAGE_VIEW') or hasAuthority('COURSE_VIEW')")
    @Operation(summary = "Get images for a course")
    public ResponseEntity<ApiResponse<List<CourseImageDTO>>> getImages(
            @PathVariable("courseId") UUID courseId,
            @RequestParam(value = "activeOnly", defaultValue = "false") boolean activeOnly) {
        List<CourseImageDTO> response = courseImageService.getImagesByCourseId(courseId, activeOnly);
        return ResponseEntity.ok(ApiResponse.success("Course images retrieved successfully", response, HttpStatus.OK.value()));
    }

    @PutMapping("/course-images/{imageId}")
    @PreAuthorize("hasAuthority('COURSE_IMAGE_UPDATE') or hasAuthority('COURSE_UPDATE')")
    @Operation(summary = "Update course image metadata")
    public ResponseEntity<ApiResponse<CourseImageDTO>> updateImage(
            @PathVariable("imageId") UUID imageId,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder,
            @RequestParam(value = "active", required = false) Boolean active) {
        CourseImageDTO response = courseImageService.updateImage(imageId, displayName, displayOrder, active);
        return ResponseEntity.ok(ApiResponse.success("Course image updated successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/course-images/{imageId}")
    @PreAuthorize("hasAuthority('COURSE_IMAGE_DELETE') or hasAuthority('COURSE_UPDATE')")
    @Operation(summary = "Delete course image")
    public ResponseEntity<ApiResponse<Void>> deleteImage(@PathVariable("imageId") UUID imageId) {
        courseImageService.deleteImage(imageId);
        return ResponseEntity.ok(ApiResponse.success("Course image deleted successfully", null, HttpStatus.OK.value()));
    }
}
