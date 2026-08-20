package com.app.datadistribution.service.interfaces;

import java.util.List;
import java.util.UUID;

import com.app.datadistribution.dto.lead.LeadAssignmentHistoryResponse;
import com.app.datadistribution.dto.lead.LeadAssignmentRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.exception.UnauthorizedException;

public interface ILeadAssignmentService {
    LeadResponse assignLead(UUID leadId, LeadAssignmentRequest request) throws UnauthorizedException;
    List<LeadAssignmentHistoryResponse> getAssignmentHistoryByLeadId(UUID leadId);
}
