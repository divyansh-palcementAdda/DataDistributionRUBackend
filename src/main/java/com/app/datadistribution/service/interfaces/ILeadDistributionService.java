package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.lead.LeadDistributionRequest;
import com.app.datadistribution.dto.lead.LeadDistributionResponse;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

public interface ILeadDistributionService {

    LeadDistributionResponse previewDistribution(LeadDistributionRequest request)
            throws BadRequestException, UnauthorizedException;

    LeadDistributionResponse distributeLeads(LeadDistributionRequest request)
            throws BadRequestException, UnauthorizedException;
}
