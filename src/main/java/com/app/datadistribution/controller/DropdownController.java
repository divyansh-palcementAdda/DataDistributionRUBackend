package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.dropdown.CourseDropdownResponse;
import com.app.datadistribution.dto.dropdown.DropdownOptionResponse;
import com.app.datadistribution.dto.dropdown.DropdownPageResponse;
import com.app.datadistribution.dto.dropdown.LeadDropdownResponse;
import com.app.datadistribution.dto.dropdown.LeadStatusDropdownResponse;
import com.app.datadistribution.dto.dropdown.UserDropdownResponse;
import com.app.datadistribution.enums.SentimentCategory;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.IDropdownService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/dropdowns")
@RequiredArgsConstructor
@Tag(name = "Dropdowns", description = "High-performance, lightweight, RBAC-aware dropdown and autocomplete APIs")
public class DropdownController {

    private final IDropdownService dropdownService;

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('DROPDOWN_USER_VIEW') or hasAuthority('USER_READ') or hasAuthority('LEAD_CREATE') or hasAuthority('LEAD_UPDATE') or hasAuthority('LEAD_ASSIGN') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Users Dropdown", description = "Retrieves active users scoped by role and department without sensitive authentication metadata")
    public ResponseEntity<ApiResponse<List<UserDropdownResponse>>> getUsersDropdown(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search) throws UnauthorizedException, AccessDeniedException, BadRequestException {

        List<UserDropdownResponse> data = dropdownService.getUsersDropdown(departmentId, role, search);
        return ResponseEntity.ok(ApiResponse.success("Users dropdown retrieved successfully", data, 200));
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('DROPDOWN_DEPARTMENT_VIEW') or hasAuthority('DEPARTMENT_VIEW') or hasAuthority('LEAD_CREATE') or hasAuthority('LEAD_UPDATE') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Departments Dropdown", description = "Retrieves active departments scoped to the caller's authorized department access")
    public ResponseEntity<ApiResponse<List<DropdownOptionResponse>>> getDepartmentsDropdown(
            @RequestParam(required = false) String search) throws UnauthorizedException, AccessDeniedException, BadRequestException {

        List<DropdownOptionResponse> data = dropdownService.getDepartmentsDropdown(search);
        return ResponseEntity.ok(ApiResponse.success("Departments dropdown retrieved successfully", data, 200));
    }

    @GetMapping("/leads")
    @PreAuthorize("hasAuthority('DROPDOWN_LEAD_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Leads Dropdown", description = "Retrieves lightweight paginated leads strictly obeying the central lead data-scoping rules")
    public ResponseEntity<ApiResponse<DropdownPageResponse<LeadDropdownResponse>>> getLeadsDropdown(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID statusId) throws UnauthorizedException, AccessDeniedException, BadRequestException {

        DropdownPageResponse<LeadDropdownResponse> data = dropdownService.getLeadsDropdown(page, size, search, departmentId, statusId);
        return ResponseEntity.ok(ApiResponse.success("Leads dropdown retrieved successfully", data, 200));
    }

    @GetMapping("/lead-statuses")
    @PreAuthorize("hasAuthority('DROPDOWN_STATUS_VIEW') or hasAuthority('LEAD_STATUS_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Lead Statuses Dropdown", description = "Retrieves active lead statuses with optional sentiment category filter")
    public ResponseEntity<ApiResponse<List<LeadStatusDropdownResponse>>> getLeadStatusesDropdown(
            @RequestParam(required = false) SentimentCategory sentimentCategory) {

        List<LeadStatusDropdownResponse> data = dropdownService.getLeadStatusesDropdown(sentimentCategory);
        return ResponseEntity.ok(ApiResponse.success("Lead statuses dropdown retrieved successfully", data, 200));
    }

    @GetMapping("/followup-statuses")
    @PreAuthorize("hasAuthority('DROPDOWN_STATUS_VIEW') or hasAuthority('FOLLOWUP_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Follow-Up Statuses Dropdown", description = "Retrieves valid lifecycle states for follow-ups (PENDING, UPCOMING, COMPLETED, CANCELLED, MISSED)")
    public ResponseEntity<ApiResponse<List<com.app.datadistribution.dto.dropdown.FollowUpStatusDropdownResponse>>> getFollowUpStatusesDropdown() {
        List<com.app.datadistribution.dto.dropdown.FollowUpStatusDropdownResponse> data = dropdownService.getFollowUpStatusesDropdown();
        return ResponseEntity.ok(ApiResponse.success("Follow-up statuses dropdown retrieved successfully", data, 200));
    }

    @GetMapping("/followup-lead-statuses")
    @PreAuthorize("hasAuthority('DROPDOWN_STATUS_VIEW') or hasAuthority('LEAD_STATUS_VIEW') or hasAuthority('FOLLOWUP_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Lead Statuses for Follow-Up Scheduling", description = "Retrieves ONLY active lead statuses configured as follow-up statuses")
    public ResponseEntity<ApiResponse<List<LeadStatusDropdownResponse>>> getFollowUpLeadStatusesDropdown() {
        List<LeadStatusDropdownResponse> data = dropdownService.getFollowUpLeadStatusesDropdown();
        return ResponseEntity.ok(ApiResponse.success("Follow-up lead statuses dropdown retrieved successfully", data, 200));
    }

    @GetMapping("/lead-sources")
    @PreAuthorize("hasAuthority('DROPDOWN_SOURCE_VIEW') or hasAuthority('LEADSOURCE_READ') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Lead Sources Dropdown", description = "Retrieves active lead sources")
    public ResponseEntity<ApiResponse<List<DropdownOptionResponse>>> getLeadSourcesDropdown(
            @RequestParam(required = false) String search) {

        List<DropdownOptionResponse> data = dropdownService.getLeadSourcesDropdown(search);
        return ResponseEntity.ok(ApiResponse.success("Lead sources dropdown retrieved successfully", data, 200));
    }

    @GetMapping("/courses")
    @PreAuthorize("hasAuthority('DROPDOWN_COURSE_VIEW') or hasAuthority('COURSE_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Courses Dropdown", description = "Retrieves active courses with optional course-type filter")
    public ResponseEntity<ApiResponse<List<CourseDropdownResponse>>> getCoursesDropdown(
            @RequestParam(required = false) UUID courseTypeId,
            @RequestParam(required = false) String search) {

        List<CourseDropdownResponse> data = dropdownService.getCoursesDropdown(courseTypeId, search);
        return ResponseEntity.ok(ApiResponse.success("Courses dropdown retrieved successfully", data, 200));
    }

    @GetMapping("/course-types")
    @PreAuthorize("hasAuthority('DROPDOWN_COURSE_TYPE_VIEW') or hasAuthority('COURSE_TYPE_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Course Types Dropdown", description = "Retrieves active course types")
    public ResponseEntity<ApiResponse<List<DropdownOptionResponse>>> getCourseTypesDropdown(
            @RequestParam(required = false) String search) {

        List<DropdownOptionResponse> data = dropdownService.getCourseTypesDropdown(search);
        return ResponseEntity.ok(ApiResponse.success("Course types dropdown retrieved successfully", data, 200));
    }

    @GetMapping("/boards")
    @PreAuthorize("hasAuthority('DROPDOWN_BOARD_VIEW') or hasAuthority('BOARD_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Boards Dropdown", description = "Retrieves active boards")
    public ResponseEntity<ApiResponse<List<DropdownOptionResponse>>> getBoardsDropdown(
            @RequestParam(required = false) String search) {

        List<DropdownOptionResponse> data = dropdownService.getBoardsDropdown(search);
        return ResponseEntity.ok(ApiResponse.success("Boards dropdown retrieved successfully", data, 200));
    }

    @GetMapping("/grades")
    @PreAuthorize("hasAuthority('DROPDOWN_GRADE_VIEW') or hasAuthority('GRADE_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get Grades Dropdown", description = "Retrieves active grades")
    public ResponseEntity<ApiResponse<List<DropdownOptionResponse>>> getGradesDropdown(
            @RequestParam(required = false) String search) {

        List<DropdownOptionResponse> data = dropdownService.getGradesDropdown(search);
        return ResponseEntity.ok(ApiResponse.success("Grades dropdown retrieved successfully", data, 200));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('DROPDOWN_ROLE_VIEW') or hasAuthority('ROLE_READ')")
    @Operation(summary = "Get Roles Dropdown", description = "Retrieves active roles for administrative selection")
    public ResponseEntity<ApiResponse<List<DropdownOptionResponse>>> getRolesDropdown(
            @RequestParam(required = false) String search) {

        List<DropdownOptionResponse> data = dropdownService.getRolesDropdown(search);
        return ResponseEntity.ok(ApiResponse.success("Roles dropdown retrieved successfully", data, 200));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('DROPDOWN_PERMISSION_VIEW') or hasAuthority('PERMISSION_READ')")
    @Operation(summary = "Get Permissions Dropdown", description = "Retrieves active permissions for administrative role management")
    public ResponseEntity<ApiResponse<List<DropdownOptionResponse>>> getPermissionsDropdown(
            @RequestParam(required = false) String search) {

        List<DropdownOptionResponse> data = dropdownService.getPermissionsDropdown(search);
        return ResponseEntity.ok(ApiResponse.success("Permissions dropdown retrieved successfully", data, 200));
    }
}
