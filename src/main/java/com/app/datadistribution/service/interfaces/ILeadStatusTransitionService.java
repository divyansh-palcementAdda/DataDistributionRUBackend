package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import java.util.List;

/**
 * Service interface for dynamic, hierarchy-aware Lead status transitions.
 */
public interface ILeadStatusTransitionService {

    /**
     * Executes the status transition with dynamic intermediate status resolution,
     * sequential branch enforcement, and daily rate limits for assigned users.
     */
    Lead executeStatusTransition(Lead lead, LeadStatus targetStatus, User currentUser, String feedbackOrRemarks) throws BadRequestException;

    /**
     * Resolves the chronological status path from current status to target status
     * using the dynamic parent-child hierarchy.
     */
    List<LeadStatus> resolveStatusPath(LeadStatus currentStatus, LeadStatus targetStatus);

    /**
     * Validates sequential progression and daily attempt rate limits.
     */
    void validateTransitionAndLimits(Lead lead, LeadStatus currentStatus, LeadStatus targetStatus, User currentUser) throws BadRequestException;
}
