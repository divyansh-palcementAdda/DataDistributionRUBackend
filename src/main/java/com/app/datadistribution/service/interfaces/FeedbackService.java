package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.feedback.FeedbackPagedResponseDTO;
import com.app.datadistribution.dto.feedback.FeedbackSummaryDTO;
import com.app.datadistribution.exception.UnauthorizedException;
import java.util.UUID;

public interface FeedbackService {
    FeedbackPagedResponseDTO getAllFeedbacks(PageRequestDTO pageRequest, UUID userId, UUID leadId) throws UnauthorizedException;
    FeedbackPagedResponseDTO getFeedbacksByUserId(UUID userId, PageRequestDTO pageRequest) throws UnauthorizedException;
    FeedbackSummaryDTO getDashboardStats() throws UnauthorizedException;
}
