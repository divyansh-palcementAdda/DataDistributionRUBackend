package com.app.datadistribution.service.interfaces;

import java.util.UUID;

import com.app.datadistribution.dto.segregation.DataSegregationCapabilitiesDTO;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.UnauthorizedException;

public interface IDataSegregationPermissionService {

    /**
     * Resolve Data Segregation flow capabilities for the currently authenticated user.
     */
    DataSegregationCapabilitiesDTO getCapabilities();

    /**
     * Resolve Data Segregation flow capabilities for a specific user entity.
     */
    DataSegregationCapabilitiesDTO getCapabilitiesForUser(User user);

    /**
     * Validate that current user has base access to Data Segregation.
     */
    void validateBaseAccess() throws UnauthorizedException;

    /**
     * Validate that current user has permission to view Course Types summary.
     */
    void validateCourseTypeAccess() throws UnauthorizedException;

    /**
     * Validate request parameters against user's permitted flow hierarchy for matrix.
     */
    void validateMatrixAccess(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) throws UnauthorizedException;

    /**
     * Validate request parameters against user's permitted flow hierarchy for user analytics.
     */
    void validateUserAnalyticsAccess(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) throws UnauthorizedException;

    /**
     * Validate request parameters against user's permitted flow hierarchy for lead status analytics.
     */
    void validateLeadStatusAnalyticsAccess(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) throws UnauthorizedException;
}
