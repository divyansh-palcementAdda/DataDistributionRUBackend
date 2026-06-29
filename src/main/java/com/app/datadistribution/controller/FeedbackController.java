package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.feedback.FeedbackPagedResponseDTO;
import com.app.datadistribution.dto.feedback.FeedbackSummaryDTO;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
@Tag(name = "Feedback Management", description = "Centralized endpoints for feedback dashboard and activities")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping
    @PreAuthorize("hasAuthority('FEEDBACK_VIEW')")
    @Operation(summary = "Get all feedbacks with search, pagination, and dynamic filtering")
    public ResponseEntity<ApiResponse<FeedbackPagedResponseDTO>> getAllFeedbacks(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "leadId", required = false) UUID leadId) throws UnauthorizedException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FeedbackPagedResponseDTO response = feedbackService.getAllFeedbacks(pageRequest, userId, leadId);
        return ResponseEntity.ok(ApiResponse.success("Feedbacks retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('FEEDBACK_VIEW')")
    @Operation(summary = "Get feedbacks created by a specific user")
    public ResponseEntity<ApiResponse<FeedbackPagedResponseDTO>> getFeedbacksByUser(
            @PathVariable("userId") UUID userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws UnauthorizedException {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        FeedbackPagedResponseDTO response = feedbackService.getFeedbacksByUserId(userId, pageRequest);
        return ResponseEntity.ok(ApiResponse.success("User feedbacks retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('FEEDBACK_VIEW')")
    @Operation(summary = "Get feedback dashboard statistics")
    public ResponseEntity<ApiResponse<FeedbackSummaryDTO>> getFeedbackDashboardStats() throws UnauthorizedException {
        FeedbackSummaryDTO response = feedbackService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success("Feedback dashboard statistics retrieved successfully", response, HttpStatus.OK.value()));
    }
}
