package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.followup.FollowUpPagedResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpSummaryDTO;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.FollowUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/followups")
@RequiredArgsConstructor
@Tag(name = "Follow-Up Management", description = "Centralized endpoints for follow-up dashboard and activities")
public class FollowUpController {

    private final FollowUpService followUpService;

    @GetMapping
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW')")
    @Operation(summary = "Get all follow-ups with search, pagination, and dynamic filtering")
    public ResponseEntity<ApiResponse<FollowUpPagedResponseDTO>> getAllFollowUps(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "followUpDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "status", required = false) FollowUpStatus status,
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "leadId", required = false) UUID leadId) throws UnauthorizedException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FollowUpPagedResponseDTO response = followUpService.getAllFollowUps(pageRequest, date, status, userId, leadId);
        return ResponseEntity.ok(ApiResponse.success("Follow-ups retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW')")
    @Operation(summary = "Get follow-ups for a specific user")
    public ResponseEntity<ApiResponse<FollowUpPagedResponseDTO>> getFollowUpsByUser(
            @PathVariable("userId") UUID userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "followUpDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws UnauthorizedException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FollowUpPagedResponseDTO response = followUpService.getFollowUpsByUserId(userId, pageRequest);
        return ResponseEntity.ok(ApiResponse.success("User follow-ups retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/today")
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW')")
    @Operation(summary = "Get today's follow-ups")
    public ResponseEntity<ApiResponse<FollowUpPagedResponseDTO>> getTodayFollowUps(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "followUpDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws UnauthorizedException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FollowUpPagedResponseDTO response = followUpService.getTodayFollowUps(pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Today's follow-ups retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW')")
    @Operation(summary = "Get pending follow-ups")
    public ResponseEntity<ApiResponse<FollowUpPagedResponseDTO>> getPendingFollowUps(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "followUpDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws UnauthorizedException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FollowUpPagedResponseDTO response = followUpService.getPendingFollowUps(pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Pending follow-ups retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/completed")
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW')")
    @Operation(summary = "Get completed follow-ups")
    public ResponseEntity<ApiResponse<FollowUpPagedResponseDTO>> getCompletedFollowUps(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "followUpDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws UnauthorizedException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FollowUpPagedResponseDTO response = followUpService.getCompletedFollowUps(pageRequest);
        return ResponseEntity.ok(ApiResponse.success("Completed follow-ups retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('FOLLOWUP_VIEW')")
    @Operation(summary = "Get follow-up dashboard statistics")
    public ResponseEntity<ApiResponse<FollowUpSummaryDTO>> getFollowUpDashboardStats() throws UnauthorizedException {
        FollowUpSummaryDTO response = followUpService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success("Follow-up dashboard statistics retrieved successfully", response, HttpStatus.OK.value()));
    }
}
