package com.app.datadistribution.service.interfaces;

import java.util.UUID;

import com.app.datadistribution.dto.lead.LeadActionEnforcementDTO;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

public interface ILeadActionEnforcementService {

    LeadActionEnforcementDTO checkActionEnforcement(UUID leadId, UUID userId)
            throws UnauthorizedException, BadRequestException;

    LeadActionEnforcementDTO checkActionEnforcement(UUID leadId)
            throws UnauthorizedException, BadRequestException;

    LeadActionEnforcementDTO checkActionEnforcement(Lead lead, User currentUser);

    boolean isLeadActionRequired(UUID leadId, UUID userId);

    boolean isQualifyingStatus(LeadStatus status);

    boolean isNotConnectedStatus(LeadStatus status);

    boolean isRegisteredStatus(LeadStatus status);

    boolean isBadStatus(LeadStatus status);

    boolean isNotInterestedStatus(LeadStatus status);
}
