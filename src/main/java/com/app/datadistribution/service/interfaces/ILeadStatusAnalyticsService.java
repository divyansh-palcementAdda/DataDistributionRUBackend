package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.analytics.CourseStatusAnalyticsResponseDTO;
import com.app.datadistribution.dto.analytics.CourseUserStatusAnalyticsResponseDTO;
import com.app.datadistribution.dto.analytics.LeadStatusAnalyticsFilterRequest;
import com.app.datadistribution.dto.analytics.UserStatusAnalyticsResponseDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

public interface ILeadStatusAnalyticsService {

    /**
     * Get Course-wise Lead Status analytics with dynamic columns, search, sort, and pagination.
     */
    CourseStatusAnalyticsResponseDTO getCourseWiseStatusCounts(LeadStatusAnalyticsFilterRequest filter)
            throws UnauthorizedException, BadRequestException;

    /**
     * Get User-wise Lead Status analytics with dynamic columns, search, sort, and pagination.
     */
    UserStatusAnalyticsResponseDTO getUserWiseStatusCounts(LeadStatusAnalyticsFilterRequest filter)
            throws UnauthorizedException, BadRequestException;

    /**
     * Get both Course-wise and User-wise Lead Status analytics simultaneously.
     */
    CourseUserStatusAnalyticsResponseDTO getCourseUserStatusAnalytics(LeadStatusAnalyticsFilterRequest filter)
            throws UnauthorizedException, BadRequestException;
}
