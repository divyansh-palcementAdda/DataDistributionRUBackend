package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.exception.UnauthorizedException;
import java.util.List;

public interface ILeadFieldSecurityService {

    /**
     * Inspects differences between existing Lead and incoming LeadRequest.
     * Validates that the current user has the required write permissions for all modified fields.
     */
    void validateFieldUpdates(Lead existingLead, LeadRequest request) throws UnauthorizedException;

    /**
     * Sanitizes a LeadResponse by nullifying fields that the current user lacks permission to view.
     */
    LeadResponse sanitizeResponse(LeadResponse response);

    /**
     * Sanitizes a list of LeadResponse objects based on the current user's field read permissions.
     */
    List<LeadResponse> sanitizeResponses(List<LeadResponse> responses);

    /**
     * Checks if current user has read permission for a given permission name.
     */
    boolean hasReadPermission(String permissionName);

    /**
     * Checks if current user has write permission for a given permission name.
     */
    boolean hasWritePermission(String permissionName);
}
