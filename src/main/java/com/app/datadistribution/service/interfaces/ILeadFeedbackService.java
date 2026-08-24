package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.lead.LeadFeedbackRequest;
import com.app.datadistribution.dto.lead.LeadFeedbackResponse;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

import java.util.List;
import java.util.UUID;

public interface ILeadFeedbackService {
    LeadFeedbackResponse addFeedback(UUID leadId, LeadFeedbackRequest request) throws UnauthorizedException, BadRequestException;
    List<LeadFeedbackResponse> getFeedbacksByLeadId(UUID leadId) throws UnauthorizedException;
}
