package com.app.datadistribution.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.BulkLeadUploadResponse;
import com.app.datadistribution.dto.lead.LeadAssignmentHistoryResponse;
import com.app.datadistribution.dto.lead.LeadAssignmentRequest;
import com.app.datadistribution.dto.lead.LeadDistributionRequest;
import com.app.datadistribution.dto.lead.LeadDistributionResponse;
import com.app.datadistribution.dto.lead.LeadFeedbackRequest;
import com.app.datadistribution.dto.lead.LeadFeedbackResponse;
import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.dto.lead.LeadPageResponse;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.lead.LeadSourceStatsResponse;
import com.app.datadistribution.dto.lead.LeadStatusChangeRequest;
import com.app.datadistribution.dto.lead.LeadStatusHistoryPageResponse;
import com.app.datadistribution.dto.lead.LeadStatusHistoryResponse;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.ICourseTemplateService;
import com.app.datadistribution.service.interfaces.ILeadAssignmentService;
import com.app.datadistribution.service.interfaces.ILeadBulkUploadService;
import com.app.datadistribution.service.interfaces.ILeadDistributionService;
import com.app.datadistribution.service.interfaces.ILeadFeedbackService;
import com.app.datadistribution.service.interfaces.ILeadFollowUpService;
import com.app.datadistribution.service.interfaces.ILeadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
@Tag(name = "Lead Management", description = "Endpoints for managing customer leads and sales pipeline")
public class LeadController {

    private final ILeadService leadService;
    private final ILeadFeedbackService leadFeedbackService;
    private final ILeadFollowUpService leadFollowUpService;
    private final ILeadAssignmentService leadAssignmentService;
    private final ILeadDistributionService leadDistributionService;
    private final ICourseTemplateService courseTemplateService;
    private final ILeadBulkUploadService leadBulkUploadService;

    @PostMapping
    @PreAuthorize("hasAuthority('LEAD_CREATE')")
    @Operation(summary = "Create a new lead")
    public ResponseEntity<ApiResponse<LeadResponse>> create(@Valid @RequestBody LeadRequest request) throws BadRequestException, UnauthorizedException {
        LeadResponse response = leadService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lead created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_UPDATE')")
    @Operation(summary = "Update an existing lead")
    public ResponseEntity<ApiResponse<LeadResponse>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LeadRequest request) {
        LeadResponse response = leadService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Lead updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    @Operation(summary = "Get lead details by ID")
    public ResponseEntity<ApiResponse<LeadResponse>> getById(@PathVariable("id") UUID id) {
        LeadResponse response = leadService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lead fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LEAD_READ')")
    @Operation(summary = "Get list of leads with pagination, sorting, search, source, status, course, interested courses, and course type filtering")
    public ResponseEntity<ApiResponse<LeadPageResponse>> getAllLeads(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sourceId", required = false) UUID sourceId,
            @RequestParam(value = "leadSourceIds", required = false) List<UUID> leadSourceIds,
            @RequestParam(value = "courseId", required = false) UUID courseId,
            @RequestParam(value = "interestedCourseIds", required = false) List<UUID> interestedCourseIds,
            @RequestParam(value = "registeredCourseId", required = false) UUID registeredCourseId,
            @RequestParam(value = "courseTypeId", required = false) UUID courseTypeId,
            @RequestParam(value = "withoutCourse", required = false) Boolean withoutCourse,
            @RequestParam(value = "statusId", required = false) UUID statusId,
            @RequestParam(value = "statusIds", required = false) List<UUID> statusIds,
            @RequestParam(value = "boardId", required = false) UUID boardId,
            @RequestParam(value = "boardIds", required = false) List<UUID> boardIds,
            @RequestParam(value = "gradeId", required = false) UUID gradeId,
            @RequestParam(value = "gradeIds", required = false) List<UUID> gradeIds) throws UnauthorizedException, BadRequestException {

        List<UUID> sourceIdsToFilter = leadSourceIds;
        if ((sourceIdsToFilter == null || sourceIdsToFilter.isEmpty()) && sourceId != null) {
            sourceIdsToFilter = List.of(sourceId);
        }

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        LeadPageResponse response = leadService.getAllLeads(pageRequest, sourceIdsToFilter, courseId, interestedCourseIds, registeredCourseId, courseTypeId, withoutCourse, statusId, statusIds, boardId, boardIds, gradeId, gradeIds);
        return ResponseEntity.ok(ApiResponse.success("Leads retrieved successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_DELETE')")
    @Operation(summary = "Soft delete a lead")
    public ResponseEntity<ApiResponse<Void>> deleteLead(@PathVariable("id") UUID id) {
        leadService.deleteLead(id);
        return ResponseEntity.ok(ApiResponse.success("Lead deleted successfully", null, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/change-status")
    @PreAuthorize("hasAuthority('LEAD_STATUS_CHANGE')")
    @Operation(summary = "Change status of a lead (feedback is mandatory)")
    public ResponseEntity<ApiResponse<LeadResponse>> changeStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LeadStatusChangeRequest request) throws BadRequestException, UnauthorizedException {
        LeadResponse response = leadService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Lead status changed successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/feedback")
    @PreAuthorize("hasAuthority('LEAD_FEEDBACK_CREATE')")
    @Operation(summary = "Add feedback to a lead")
    public ResponseEntity<ApiResponse<LeadFeedbackResponse>> addFeedback(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LeadFeedbackRequest request) throws UnauthorizedException {
        LeadFeedbackResponse response = leadFeedbackService.addFeedback(id, request);
        return ResponseEntity.ok(ApiResponse.success("Feedback added successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}/feedbacks")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    @Operation(summary = "Get feedback history for a lead")
    public ResponseEntity<ApiResponse<List<LeadFeedbackResponse>>> getFeedbacks(@PathVariable("id") UUID id) {
        List<LeadFeedbackResponse> response = leadFeedbackService.getFeedbacksByLeadId(id);
        return ResponseEntity.ok(ApiResponse.success("Lead feedbacks fetched successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/followups")
    @PreAuthorize("hasAuthority('LEAD_FOLLOWUP_CREATE')")
    @Operation(summary = "Schedule a follow-up for a lead")
    public ResponseEntity<ApiResponse<LeadFollowUpResponse>> createFollowUp(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LeadFollowUpRequest request) throws UnauthorizedException {
        LeadFollowUpResponse response = leadFollowUpService.createFollowUp(id, request);
        return ResponseEntity.ok(ApiResponse.success("Follow-up scheduled successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}/followups")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    @Operation(summary = "Get follow-ups for a lead")
    public ResponseEntity<ApiResponse<List<LeadFollowUpResponse>>> getFollowUps(@PathVariable("id") UUID id) {
        List<LeadFollowUpResponse> response = leadFollowUpService.getFollowUpsByLeadId(id);
        return ResponseEntity.ok(ApiResponse.success("Lead follow-ups fetched successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/followups/{followUpId}/complete")
    @PreAuthorize("hasAuthority('LEAD_FOLLOWUP_CREATE')")
    @Operation(summary = "Mark a follow-up as completed")
    public ResponseEntity<ApiResponse<LeadFollowUpResponse>> completeFollowUp(
            @PathVariable("followUpId") UUID followUpId,
            @RequestParam(value = "remarks", required = false) String remarks) {
        LeadFollowUpResponse response = leadFollowUpService.completeFollowUp(followUpId, remarks);
        return ResponseEntity.ok(ApiResponse.success("Follow-up marked completed successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('LEAD_ASSIGN')")
    @Operation(summary = "Assign/reassign a lead to a user")
    public ResponseEntity<ApiResponse<LeadResponse>> assignLead(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LeadAssignmentRequest request) throws UnauthorizedException {
        LeadResponse response = leadAssignmentService.assignLead(id, request);
        return ResponseEntity.ok(ApiResponse.success("Lead assigned successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}/assignment-history")
    @PreAuthorize("hasAuthority('LEAD_HISTORY_READ')")
    @Operation(summary = "Get assignment history for a lead")
    public ResponseEntity<ApiResponse<List<LeadAssignmentHistoryResponse>>> getAssignmentHistory(@PathVariable("id") UUID id) {
        List<LeadAssignmentHistoryResponse> response = leadAssignmentService.getAssignmentHistoryByLeadId(id);
        return ResponseEntity.ok(ApiResponse.success("Lead assignment history fetched successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/distribute/preview")
    @PreAuthorize("hasAuthority('LEAD_DISTRIBUTE') or hasAuthority('LEAD_DISTRIBUTION_PREVIEW') or hasAuthority('LEAD_ASSIGN')")
    @Operation(summary = "Preview manual lead distribution calculation and user capacity limits")
    public ResponseEntity<ApiResponse<LeadDistributionResponse>> previewLeadDistribution(
            @Valid @RequestBody LeadDistributionRequest request) throws BadRequestException, UnauthorizedException {
        LeadDistributionResponse response = leadDistributionService.previewDistribution(request);
        return ResponseEntity.ok(ApiResponse.success("Lead distribution preview calculated successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/distribute")
    @PreAuthorize("hasAuthority('LEAD_DISTRIBUTE') or hasAuthority('LEAD_ASSIGN')")
    @Operation(summary = "Execute manual rule-based lead distribution among selected users")
    public ResponseEntity<ApiResponse<LeadDistributionResponse>> distributeLeads(
            @Valid @RequestBody LeadDistributionRequest request) throws BadRequestException, UnauthorizedException {
        LeadDistributionResponse response = leadDistributionService.distributeLeads(request);
        return ResponseEntity.ok(ApiResponse.success("Leads distributed successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAuthority('LEAD_STATUS_HISTORY_VIEW') or hasAuthority('LEAD_HISTORY_READ') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get status history for a lead with optional pagination and sorting")
    public ResponseEntity<?> getStatusHistory(
            @PathVariable("id") UUID id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection) throws UnauthorizedException, BadRequestException {
        if (page != null && size != null) {
            PageRequestDTO pageRequest = PageRequestDTO.builder()
                    .page(page)
                    .size(size)
                    .sortBy(sortBy)
                    .sortDirection(sortDirection)
                    .build();
            LeadStatusHistoryPageResponse response = leadService.getStatusHistoryByLeadId(id, pageRequest);
            return ResponseEntity.ok(ApiResponse.success("Lead status history fetched successfully", response, HttpStatus.OK.value()));
        }
        List<LeadStatusHistoryResponse> response = leadService.getStatusHistoryByLeadId(id);
        return ResponseEntity.ok(ApiResponse.success("Lead status history fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/stats/source-wise")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    @Operation(summary = "Get lead stats source-wise")
    public ResponseEntity<ApiResponse<List<LeadSourceStatsResponse>>> getSourceWiseStats() throws UnauthorizedException, BadRequestException {
        List<LeadSourceStatsResponse> response = leadService.getSourceWiseStats();
        return ResponseEntity.ok(ApiResponse.success("Source-wise lead statistics retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/stats/status-wise")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    @Operation(summary = "Get lead stats status-wise")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStatusWiseStats() throws UnauthorizedException, BadRequestException {
        Map<String, Long> response = leadService.getStatusWiseStats();
        return ResponseEntity.ok(ApiResponse.success("Status-wise lead statistics retrieved successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/interested-courses")
    @PreAuthorize("hasAuthority('LEAD_INTERESTED_COURSE_UPDATE') or hasAuthority('LEAD_UPDATE')")
    @Operation(summary = "Add interested courses to a lead")
    public ResponseEntity<ApiResponse<LeadResponse>> addInterestedCourses(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, List<UUID>> requestBody) {
        List<UUID> courseIds = requestBody != null ? requestBody.get("courseIds") : null;
        LeadResponse response = leadService.addInterestedCourses(id, courseIds);
        return ResponseEntity.ok(ApiResponse.success("Interested courses added to lead successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}/interested-courses/{courseId}")
    @PreAuthorize("hasAuthority('LEAD_INTERESTED_COURSE_UPDATE') or hasAuthority('LEAD_UPDATE')")
    @Operation(summary = "Remove an interested course from a lead")
    public ResponseEntity<ApiResponse<LeadResponse>> removeInterestedCourse(
            @PathVariable("id") UUID id,
            @PathVariable("courseId") UUID courseId) {
        LeadResponse response = leadService.removeInterestedCourse(id, courseId);
        return ResponseEntity.ok(ApiResponse.success("Interested course removed from lead successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/register-course/{courseId}")
    @PreAuthorize("hasAuthority('LEAD_REGISTERED_COURSE_UPDATE') or hasAuthority('LEAD_UPDATE')")
    @Operation(summary = "Register lead in a specific course")
    public ResponseEntity<ApiResponse<LeadResponse>> registerCourse(
            @PathVariable("id") UUID id,
            @PathVariable("courseId") UUID courseId) {
        LeadResponse response = leadService.registerCourse(id, courseId);
        return ResponseEntity.ok(ApiResponse.success("Lead registered in course successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}/course-templates")
    @PreAuthorize("hasAuthority('COURSE_TEMPLATE_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get course templates applicable for a lead")
    public ResponseEntity<ApiResponse<List<com.app.datadistribution.dto.coursetemplate.CourseTemplateResponseDTO>>> getCourseTemplatesForLead(@PathVariable("id") UUID id) {
        List<com.app.datadistribution.dto.coursetemplate.CourseTemplateResponseDTO> response = courseTemplateService.getTemplatesForLead(id);
        return ResponseEntity.ok(ApiResponse.success("Applicable course templates retrieved successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/{id}/send-template/{templateId}")
    @PreAuthorize("hasAuthority('COURSE_TEMPLATE_SEND') or hasAuthority('LEAD_UPDATE')")
    @Operation(summary = "Send a course template to a lead")
    public ResponseEntity<ApiResponse<Void>> sendTemplateToLead(
            @PathVariable("id") UUID id,
            @PathVariable("templateId") UUID templateId) throws UnauthorizedException {
        courseTemplateService.sendTemplateToLead(id, templateId);
        return ResponseEntity.ok(ApiResponse.success("Course template sent to lead successfully", null, HttpStatus.OK.value()));
    }

    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('LEAD_BULK_UPLOAD') or hasAuthority('LEAD_CREATE')")
    @Operation(summary = "Bulk upload leads from an Excel file with selected master-data mappings")
    public ResponseEntity<ApiResponse<BulkLeadUploadResponse>> bulkUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "courseTypeId", required = false) UUID courseTypeId,
            @RequestParam(value = "gradeId", required = false) UUID gradeId,
            @RequestParam(value = "boardId", required = false) UUID boardId,
            @RequestParam(value = "leadSourceId", required = false) UUID leadSourceId,
            @RequestParam(value = "leadSourceIds", required = false) List<UUID> leadSourceIds,
            @RequestParam(value = "statusId", required = false) UUID statusId,
            @RequestParam(value = "departmentId", required = false) UUID departmentId,
            @RequestParam(value = "assignedToUserId", required = false) UUID assignedToUserId) throws BadRequestException, UnauthorizedException {

        BulkLeadUploadResponse response = leadBulkUploadService.bulkUploadLeads(
                file, courseTypeId, gradeId, boardId, leadSourceId, leadSourceIds, statusId, departmentId, assignedToUserId
        );
        return ResponseEntity.ok(ApiResponse.success("Lead bulk upload processed successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/bulk-upload/template")
    @PreAuthorize("hasAuthority('LEAD_BULK_UPLOAD_TEMPLATE_DOWNLOAD') or hasAuthority('LEAD_BULK_UPLOAD') or hasAuthority('LEAD_CREATE') or hasAuthority('LEAD_READ')")
    @Operation(
            summary = "Download standard Excel template for bulk lead upload",
            description = "Generates and downloads the official 2-sheet Lead Bulk Upload Excel (.xlsx) template. "
                    + "Sheet 1 ('Lead Upload') contains styled, frozen column headers. Sheet 2 ('Instructions') contains column definitions, "
                    + "field requirement rules, validation formats, duplicate detection policies, and UI-selected master data guidance."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Excel template generated successfully",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    )
    public ResponseEntity<byte[]> downloadBulkUploadTemplate() {
        byte[] templateBytes = leadBulkUploadService.downloadTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "lead-bulk-upload-template.xlsx");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        return new ResponseEntity<>(templateBytes, headers, HttpStatus.OK);
    }
}
