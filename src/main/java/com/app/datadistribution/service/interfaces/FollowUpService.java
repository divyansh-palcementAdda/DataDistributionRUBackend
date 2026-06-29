package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.followup.FollowUpPagedResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpSummaryDTO;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.UnauthorizedException;
import java.time.LocalDate;
import java.util.UUID;

public interface FollowUpService {
    FollowUpPagedResponseDTO getAllFollowUps(PageRequestDTO pageRequest, LocalDate date, FollowUpStatus status, UUID userId, UUID leadId) throws UnauthorizedException;
    FollowUpPagedResponseDTO getFollowUpsByUserId(UUID userId, PageRequestDTO pageRequest) throws UnauthorizedException;
    FollowUpPagedResponseDTO getTodayFollowUps(PageRequestDTO pageRequest) throws UnauthorizedException;
    FollowUpPagedResponseDTO getPendingFollowUps(PageRequestDTO pageRequest) throws UnauthorizedException;
    FollowUpPagedResponseDTO getCompletedFollowUps(PageRequestDTO pageRequest) throws UnauthorizedException;
    FollowUpSummaryDTO getDashboardStats() throws UnauthorizedException;
}
