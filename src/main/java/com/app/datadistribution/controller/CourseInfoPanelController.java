package com.app.datadistribution.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.infopanel.CompetitorRequestDTO;
import com.app.datadistribution.dto.infopanel.CompetitorResponseDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelRequestDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelResponseDTO;
import com.app.datadistribution.dto.infopanel.InfoPanelPermissionMatrixDTO;
import com.app.datadistribution.dto.infopanel.LeadCallerGuidanceResponseDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.ICourseInfoPanelService;

import com.app.datadistribution.dto.infopanel.CourseInfoPanelBulkUploadResponseDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelPreviewResponseDTO;
import com.app.datadistribution.service.interfaces.ICourseInfoPanelBulkUploadService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/info-panels", "/api/course-info-panel"})
@RequiredArgsConstructor
@Validated
@Tag(name = "Info Panel & Caller Guidance", description = "Endpoints for course info panels, caller guidance, competitor comparisons, and bulk upload")
public class CourseInfoPanelController {

    private final ICourseInfoPanelService infoPanelService;
    private final ICourseInfoPanelBulkUploadService bulkUploadService;

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAuthority('INFO_PANEL_VIEW') or hasAuthority('COURSE_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Info Panel & Guidance data by Course ID and optional Academic Session")
    public ResponseEntity<ApiResponse<CourseInfoPanelResponseDTO>> getByCourseId(
            @PathVariable("courseId") UUID courseId,
            @RequestParam(value = "academicSession", required = false) String session) {
        CourseInfoPanelResponseDTO response = infoPanelService.getInfoPanelByCourseId(courseId, session);
        return ResponseEntity.ok(ApiResponse.success("Info panel retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/lead/{leadId}")
    @PreAuthorize("hasAuthority('INFO_PANEL_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get complete Caller Guidance and Info Panel for a Lead's interested course(s)")
    public ResponseEntity<ApiResponse<LeadCallerGuidanceResponseDTO>> getCallerGuidanceForLead(
            @PathVariable("leadId") UUID leadId,
            @RequestParam(value = "courseId", required = false) UUID courseId) {
        LeadCallerGuidanceResponseDTO response = infoPanelService.getCallerGuidanceForLead(leadId, courseId);
        return ResponseEntity.ok(ApiResponse.success("Caller guidance retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INFO_PANEL_VIEW') or hasAuthority('COURSE_VIEW')")
    @Operation(summary = "Get Info Panel by ID")
    public ResponseEntity<ApiResponse<CourseInfoPanelResponseDTO>> getById(@PathVariable("id") UUID id) {
        CourseInfoPanelResponseDTO response = infoPanelService.getInfoPanelById(id);
        return ResponseEntity.ok(ApiResponse.success("Info panel retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/permissions")
    @Operation(summary = "Get field-level RBAC permission matrix for Info Panel")
    public ResponseEntity<ApiResponse<InfoPanelPermissionMatrixDTO>> getPermissions() {
        InfoPanelPermissionMatrixDTO matrix = infoPanelService.getPermissionMatrix();
        return ResponseEntity.ok(ApiResponse.success("Info panel permissions retrieved successfully", matrix, HttpStatus.OK.value()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INFO_PANEL_CREATE') or hasAuthority('INFO_PANEL_MANAGE')")
    @Operation(summary = "Create a new Course Info Panel")
    public ResponseEntity<ApiResponse<CourseInfoPanelResponseDTO>> create(
            @Valid @RequestBody CourseInfoPanelRequestDTO request) throws BadRequestException, UnauthorizedException {
        CourseInfoPanelResponseDTO response = infoPanelService.createInfoPanel(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Info panel created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INFO_PANEL_UPDATE') or hasAuthority('INFO_PANEL_MANAGE')")
    @Operation(summary = "Update an existing Course Info Panel")
    public ResponseEntity<ApiResponse<CourseInfoPanelResponseDTO>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CourseInfoPanelRequestDTO request) throws UnauthorizedException {
        CourseInfoPanelResponseDTO response = infoPanelService.updateInfoPanel(id, request);
        return ResponseEntity.ok(ApiResponse.success("Info panel updated successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INFO_PANEL_DELETE') or hasAuthority('INFO_PANEL_MANAGE')")
    @Operation(summary = "Delete / Deactivate an Info Panel")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") UUID id) {
        infoPanelService.deleteInfoPanel(id);
        return ResponseEntity.ok(ApiResponse.success("Info panel deleted successfully", null, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/competitors")
    @PreAuthorize("hasAuthority('INFO_PANEL_UPDATE') or hasAuthority('INFO_PANEL_MANAGE')")
    @Operation(summary = "Add a competitor college to an Info Panel")
    public ResponseEntity<ApiResponse<CompetitorResponseDTO>> addCompetitor(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CompetitorRequestDTO request) throws BadRequestException {
        CompetitorResponseDTO response = infoPanelService.addCompetitor(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Competitor added successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}/competitors/{competitorId}")
    @PreAuthorize("hasAuthority('INFO_PANEL_UPDATE') or hasAuthority('INFO_PANEL_MANAGE')")
    @Operation(summary = "Update a competitor college and its comparison data")
    public ResponseEntity<ApiResponse<CompetitorResponseDTO>> updateCompetitor(
            @PathVariable("id") UUID id,
            @PathVariable("competitorId") UUID competitorId,
            @Valid @RequestBody CompetitorRequestDTO request) {
        CompetitorResponseDTO response = infoPanelService.updateCompetitor(id, competitorId, request);
        return ResponseEntity.ok(ApiResponse.success("Competitor updated successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}/competitors/{competitorId}")
    @PreAuthorize("hasAuthority('INFO_PANEL_UPDATE') or hasAuthority('INFO_PANEL_MANAGE')")
    @Operation(summary = "Delete a competitor college")
    public ResponseEntity<ApiResponse<Void>> deleteCompetitor(
            @PathVariable("id") UUID id,
            @PathVariable("competitorId") UUID competitorId) {
        infoPanelService.deleteCompetitor(id, competitorId);
        return ResponseEntity.ok(ApiResponse.success("Competitor deleted successfully", null, HttpStatus.OK.value()));
    }

    @PutMapping("/{id}/competitors/reorder")
    @PreAuthorize("hasAuthority('INFO_PANEL_UPDATE') or hasAuthority('INFO_PANEL_MANAGE')")
    @Operation(summary = "Reorder competitor colleges for an Info Panel")
    public ResponseEntity<ApiResponse<Void>> reorderCompetitors(
            @PathVariable("id") UUID id,
            @RequestBody List<UUID> competitorIds) {
        infoPanelService.reorderCompetitors(id, competitorIds);
        return ResponseEntity.ok(ApiResponse.success("Competitors reordered successfully", null, HttpStatus.OK.value()));
    }

    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('COURSE_INFO_PANEL_BULK_UPLOAD') or hasAuthority('INFO_PANEL_CREATE') or hasAuthority('INFO_PANEL_MANAGE')")
    @Operation(summary = "Bulk upload and update Course Info Panel and competitor data from Excel")
    public ResponseEntity<ApiResponse<CourseInfoPanelBulkUploadResponseDTO>> bulkUpload(
            @RequestParam("file") MultipartFile file) throws BadRequestException {
        CourseInfoPanelBulkUploadResponseDTO response = bulkUploadService.bulkUpload(file);
        return ResponseEntity.ok(ApiResponse.success("Course Info Panel bulk import completed", response, HttpStatus.OK.value()));
    }

    @PostMapping(value = "/bulk-upload/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('COURSE_INFO_PANEL_BULK_UPLOAD') or hasAuthority('INFO_PANEL_CREATE') or hasAuthority('INFO_PANEL_MANAGE')")
    @Operation(summary = "Validate and preview Course Info Panel Excel import without committing changes")
    public ResponseEntity<ApiResponse<CourseInfoPanelPreviewResponseDTO>> validateBulkUpload(
            @RequestParam("file") MultipartFile file) throws BadRequestException {
        CourseInfoPanelPreviewResponseDTO response = bulkUploadService.validateExcel(file);
        return ResponseEntity.ok(ApiResponse.success("Excel validation preview generated", response, HttpStatus.OK.value()));
    }

    @GetMapping("/bulk-upload/template")
    @PreAuthorize("hasAuthority('COURSE_INFO_PANEL_TEMPLATE_DOWNLOAD') or hasAuthority('COURSE_INFO_PANEL_BULK_UPLOAD') or hasAuthority('INFO_PANEL_VIEW') or hasAuthority('INFO_PANEL_MANAGE')")
    @Operation(summary = "Download official Course Info Panel bulk upload Excel template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] excelBytes = bulkUploadService.generateTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("course_info_panel_template.xlsx").build());
        headers.setContentLength(excelBytes.length);
        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/bulk-upload/{importId}/error-file")
    @PreAuthorize("hasAuthority('COURSE_INFO_PANEL_ERROR_DOWNLOAD') or hasAuthority('COURSE_INFO_PANEL_BULK_UPLOAD') or hasAuthority('INFO_PANEL_MANAGE')")
    @Operation(summary = "Download error sheet for an executed or validated bulk upload")
    public ResponseEntity<byte[]> downloadErrorFile(@PathVariable("importId") UUID importId) {
        byte[] errorFileBytes = bulkUploadService.getErrorFile(importId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("course_info_panel_errors_" + importId + ".xlsx").build());
        headers.setContentLength(errorFileBytes.length);
        return new ResponseEntity<>(errorFileBytes, headers, HttpStatus.OK);
    }
}
