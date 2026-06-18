package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.exception.UnauthorizedException;

import java.util.List;
import java.util.UUID;

public interface ILeadFollowUpService {
    LeadFollowUpResponse createFollowUp(UUID leadId, LeadFollowUpRequest request) throws UnauthorizedException;
    List<LeadFollowUpResponse> getFollowUpsByLeadId(UUID leadId);
    LeadFollowUpResponse completeFollowUp(UUID followUpId, String remarks);
}
