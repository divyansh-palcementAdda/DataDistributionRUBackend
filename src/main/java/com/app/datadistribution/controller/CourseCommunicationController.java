package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.communication.CourseCommunicationConfigDTO;
import com.app.datadistribution.dto.communication.InfoPanelResponseDTO;
import com.app.datadistribution.dto.communication.SendCommunicationRequestDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.ICourseCommunicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Course Communication Management", description = "Endpoints for channel default configurations, Info Panel, and Email/WhatsApp sending")
public class CourseCommunicationController {

    private final ICourseCommunicationService communicationService;

    @GetMapping("/courses/{courseId}/communication-config")
    @PreAuthorize("hasAuthority('COURSE_TEMPLATE_VIEW') or hasAuthority('COURSE_VIEW')")
    @Operation(summary = "Get channel communication default config for a course")
    public ResponseEntity<ApiResponse<CourseCommunicationConfigDTO>> getCommunicationConfig(@PathVariable("courseId") UUID courseId) {
        CourseCommunicationConfigDTO response = communicationService.getCommunicationConfig(courseId);
        return ResponseEntity.ok(ApiResponse.success("Course communication config retrieved successfully", response, HttpStatus.OK.value()));
    }

    @PutMapping("/courses/{courseId}/communication-config")
    @PreAuthorize("hasAuthority('COURSE_TEMPLATE_UPDATE') or hasAuthority('COURSE_UPDATE')")
    @Operation(summary = "Update channel communication default config for a course")
    public ResponseEntity<ApiResponse<CourseCommunicationConfigDTO>> updateCommunicationConfig(
            @PathVariable("courseId") UUID courseId,
            @RequestBody CourseCommunicationConfigDTO configDTO) throws BadRequestException {
        CourseCommunicationConfigDTO response = communicationService.updateCommunicationConfig(courseId, configDTO);
        return ResponseEntity.ok(ApiResponse.success("Course communication config updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/leads/{leadId}/info-panel")
    @PreAuthorize("hasAuthority('LEAD_READ')")
    @Operation(summary = "Get rendered Info Panel data for a lead")
    public ResponseEntity<ApiResponse<InfoPanelResponseDTO>> getInfoPanel(
            @PathVariable("leadId") UUID leadId,
            @RequestParam(value = "courseId", required = false) UUID courseId) {
        InfoPanelResponseDTO response = communicationService.getInfoPanelForLead(leadId, courseId);
        return ResponseEntity.ok(ApiResponse.success("Info panel data retrieved successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping({"/leads/{leadId}/course-template/email", "/leads/{leadId}/send-course-email"})
    @PreAuthorize("hasAuthority('COURSE_TEMPLATE_SEND_EMAIL') or hasAuthority('COURSE_TEMPLATE_SEND') or hasAuthority('LEAD_UPDATE')")
    @Operation(summary = "Send Course Email to lead with optional permitted image override")
    public ResponseEntity<ApiResponse<Void>> sendCourseEmail(
            @PathVariable("leadId") UUID leadId,
            @RequestBody SendCommunicationRequestDTO request) throws BadRequestException, UnauthorizedException {
        communicationService.sendCourseEmail(leadId, request);
        return ResponseEntity.ok(ApiResponse.success("Course Email dispatched successfully", null, HttpStatus.OK.value()));
    }

    @PostMapping({"/leads/{leadId}/course-template/whatsapp", "/leads/{leadId}/send-course-whatsapp"})
    @PreAuthorize("hasAuthority('COURSE_TEMPLATE_SEND_WHATSAPP') or hasAuthority('COURSE_TEMPLATE_SEND') or hasAuthority('LEAD_UPDATE')")
    @Operation(summary = "Send Course WhatsApp message to lead with optional permitted image override")
    public ResponseEntity<ApiResponse<Void>> sendCourseWhatsApp(
            @PathVariable("leadId") UUID leadId,
            @RequestBody SendCommunicationRequestDTO request) throws BadRequestException, UnauthorizedException {
        communicationService.sendCourseWhatsApp(leadId, request);
        return ResponseEntity.ok(ApiResponse.success("Course WhatsApp message dispatched successfully", null, HttpStatus.OK.value()));
    }
}
