package com.app.datadistribution.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.lead.FollowUpOpenResponseDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.ILeadFollowUpService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/lead-follow-ups")
@RequiredArgsConstructor
@Tag(name = "Lead Follow-Up Lifecycle", description = "Endpoints for lead follow-up opening and mandatory action lifecycle")
public class LeadFollowUpOpenController {

    private final ILeadFollowUpService leadFollowUpService;

    @PostMapping("/{followUpId}/open")
    @PreAuthorize("hasAuthority('FOLLOW_UP_OPEN') or hasAuthority('FOLLOWUP_VIEW') or hasAuthority('LEAD_READ')")
    @Operation(summary = "Open a lead follow-up and automatically mark it as completed if scheduled for today")
    public ResponseEntity<ApiResponse<FollowUpOpenResponseDTO>> openFollowUp(
            @PathVariable("followUpId") UUID followUpId) throws UnauthorizedException, BadRequestException {
        FollowUpOpenResponseDTO response = leadFollowUpService.openFollowUp(followUpId);
        return ResponseEntity.ok(ApiResponse.success("Follow-up opened and marked as completed", response, HttpStatus.OK.value()));
    }
}
