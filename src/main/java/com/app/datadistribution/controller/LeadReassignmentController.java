package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.reassign.*;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.ILeadReassignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Lead & Follow-up Reassignment", description = "APIs for reassigning and distributing leads and follow-ups between users")
public class LeadReassignmentController {

    private final ILeadReassignmentService leadReassignmentService;

    @GetMapping("/follow-ups/reassignable")
    @PreAuthorize("hasAuthority('FOLLOW_UP_REASSIGN') or hasAuthority('FOLLOW_UP_BULK_REASSIGN') or hasAuthority('FOLLOWUP_READ') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get paginated reassignable follow-ups for a user")
    public ResponseEntity<ApiResponse<ReassignablePageResponseDTO<FollowUpReassignableDTO>>> getReassignableFollowUps(
            @RequestParam("responsibleUserId") UUID responsibleUserId,
            @RequestParam(value = "scheduledDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledDate,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "status", required = false) FollowUpStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "followUpDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search)
            throws UnauthorizedException, BadRequestException, ResourcesNotFoundException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        ReassignablePageResponseDTO<FollowUpReassignableDTO> result = leadReassignmentService.getReassignableFollowUps(
                responsibleUserId, scheduledDate, fromDate, toDate, status, pageRequest);

        return ResponseEntity.ok(ApiResponse.success("Reassignable follow-ups retrieved successfully", result, HttpStatus.OK.value()));
    }

    @PostMapping("/follow-ups/reassign")
    @PreAuthorize("hasAuthority('FOLLOW_UP_REASSIGN') or hasAuthority('FOLLOW_UP_BULK_REASSIGN')")
    @Operation(summary = "Reassign or distribute follow-ups from one user to target user(s)")
    public ResponseEntity<ApiResponse<FollowUpReassignResponse>> reassignFollowUps(
            @Valid @RequestBody FollowUpReassignRequest request)
            throws UnauthorizedException, BadRequestException, ResourcesNotFoundException {

        FollowUpReassignResponse response = leadReassignmentService.reassignFollowUps(request);
        return ResponseEntity.ok(ApiResponse.success("Follow-ups reassigned successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/follow-ups/reassign/distribute")
    @PreAuthorize("hasAuthority('FOLLOW_UP_BULK_REASSIGN') or hasAuthority('FOLLOW_UP_REASSIGN')")
    @Operation(summary = "Distribute follow-ups among multiple target users")
    public ResponseEntity<ApiResponse<FollowUpReassignResponse>> distributeFollowUps(
            @Valid @RequestBody FollowUpReassignRequest request)
            throws UnauthorizedException, BadRequestException, ResourcesNotFoundException {

        FollowUpReassignResponse response = leadReassignmentService.reassignFollowUps(request);
        return ResponseEntity.ok(ApiResponse.success("Follow-ups distributed successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/leads/reassignable")
    @PreAuthorize("hasAuthority('LEAD_REASSIGN') or hasAuthority('LEAD_BULK_REASSIGN') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Get paginated reassignable leads for an assigned user")
    public ResponseEntity<ApiResponse<ReassignablePageResponseDTO<LeadResponse>>> getReassignableLeads(
            @RequestParam("assignedUserId") UUID assignedUserId,
            @RequestParam(value = "courseTypeId", required = false) UUID courseTypeId,
            @RequestParam(value = "gradeId", required = false) UUID gradeId,
            @RequestParam(value = "boardId", required = false) UUID boardId,
            @RequestParam(value = "leadSourceId", required = false) UUID leadSourceId,
            @RequestParam(value = "statusId", required = false) UUID statusId,
            @RequestParam(value = "departmentId", required = false) UUID departmentId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection,
            @RequestParam(value = "search", required = false) String search)
            throws UnauthorizedException, BadRequestException, ResourcesNotFoundException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        ReassignablePageResponseDTO<LeadResponse> result = leadReassignmentService.getReassignableLeads(
                assignedUserId, courseTypeId, gradeId, boardId, leadSourceId, statusId, departmentId, pageRequest);

        return ResponseEntity.ok(ApiResponse.success("Reassignable leads retrieved successfully", result, HttpStatus.OK.value()));
    }

    @PostMapping("/leads/reassign")
    @PreAuthorize("hasAuthority('LEAD_REASSIGN') or hasAuthority('LEAD_BULK_REASSIGN')")
    @Operation(summary = "Reassign or distribute leads from one user to target user(s)")
    public ResponseEntity<ApiResponse<LeadReassignResponse>> reassignLeads(
            @Valid @RequestBody LeadReassignRequest request)
            throws UnauthorizedException, BadRequestException, ResourcesNotFoundException {

        LeadReassignResponse response = leadReassignmentService.reassignLeads(request);
        return ResponseEntity.ok(ApiResponse.success("Leads reassigned successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping("/leads/reassign/distribute")
    @PreAuthorize("hasAuthority('LEAD_BULK_REASSIGN') or hasAuthority('LEAD_REASSIGN')")
    @Operation(summary = "Distribute leads among multiple target users")
    public ResponseEntity<ApiResponse<LeadReassignResponse>> distributeLeads(
            @Valid @RequestBody LeadReassignRequest request)
            throws UnauthorizedException, BadRequestException, ResourcesNotFoundException {

        LeadReassignResponse response = leadReassignmentService.reassignLeads(request);
        return ResponseEntity.ok(ApiResponse.success("Leads distributed successfully", response, HttpStatus.OK.value()));
    }
}
