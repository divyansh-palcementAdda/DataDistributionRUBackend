package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.lead.CancelFollowUpRequest;
import com.app.datadistribution.dto.lead.CompleteFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.dto.lead.RescheduleFollowUpRequest;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

import java.util.List;
import java.util.UUID;

public interface ILeadFollowUpService {
    LeadFollowUpResponse createFollowUp(UUID leadId, LeadFollowUpRequest request) throws UnauthorizedException, BadRequestException;
    LeadFollowUpResponse createFollowUp(LeadFollowUpRequest request) throws UnauthorizedException, BadRequestException;
    List<LeadFollowUpResponse> getFollowUpsByLeadId(UUID leadId) throws UnauthorizedException;
    LeadFollowUpResponse rescheduleFollowUp(UUID followUpId, RescheduleFollowUpRequest request) throws UnauthorizedException, BadRequestException;
    LeadFollowUpResponse completeFollowUp(UUID followUpId, String remarks) throws UnauthorizedException, BadRequestException;
    LeadFollowUpResponse completeFollowUp(UUID followUpId, CompleteFollowUpRequest request) throws UnauthorizedException, BadRequestException;
    LeadFollowUpResponse cancelFollowUp(UUID followUpId, String remarks) throws UnauthorizedException, BadRequestException;
    LeadFollowUpResponse cancelFollowUp(UUID followUpId, CancelFollowUpRequest request) throws UnauthorizedException, BadRequestException;
}

