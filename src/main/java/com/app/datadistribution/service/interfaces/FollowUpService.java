package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.followup.FollowUpPagedResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpSummaryDTO;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FollowUpService {
    default FollowUpPagedResponseDTO getAllFollowUps(PageRequestDTO pageRequest, LocalDate date, FollowUpStatus status, UUID userId, UUID leadId) throws UnauthorizedException, BadRequestException {
        return getAllFollowUps(pageRequest, date, status, userId, leadId, null, null);
    }

    FollowUpPagedResponseDTO getAllFollowUps(PageRequestDTO pageRequest, LocalDate date, FollowUpStatus status, UUID userId, UUID leadId, UUID leadStatusId, List<UUID> leadStatusIds) throws UnauthorizedException, BadRequestException;
    FollowUpPagedResponseDTO getFollowUpsByUserId(UUID userId, PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException;
    FollowUpPagedResponseDTO getTodayFollowUps(PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException;
    FollowUpPagedResponseDTO getPendingFollowUps(PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException;
    FollowUpPagedResponseDTO getCompletedFollowUps(PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException;
    FollowUpSummaryDTO getDashboardStats() throws UnauthorizedException, BadRequestException;
    List<com.app.datadistribution.dto.followup.FollowUpStatusCountDTO> getFollowUpStatusCounts() throws UnauthorizedException, BadRequestException;
}
