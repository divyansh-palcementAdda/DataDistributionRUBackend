package com.app.datadistribution.service.interfaces;

import java.util.List;
import java.util.UUID;

import com.app.datadistribution.dto.segregation.CourseSegregationResponseDTO;
import com.app.datadistribution.dto.segregation.CourseUserSegregationResponseDTO;
import com.app.datadistribution.dto.segregation.CourseTypeSegregationDTO;
import com.app.datadistribution.dto.segregation.DataSegregationCapabilitiesDTO;
import com.app.datadistribution.dto.segregation.LeadStatusAnalyticsDTO;
import com.app.datadistribution.dto.segregation.SegregationMatrixResponseDTO;
import com.app.datadistribution.dto.segregation.UserSegregationAnalyticsDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

import com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest;
import com.app.datadistribution.dto.segregation.UserAllocationSummaryDTO;
import com.app.datadistribution.dto.segregation.UserAllocationUsersResponseDTO;

public interface IDataSegregationService {

    DataSegregationCapabilitiesDTO getCapabilities();

    List<CourseTypeSegregationDTO> getCourseTypesSummary() throws UnauthorizedException, BadRequestException;

    SegregationMatrixResponseDTO getSegregationMatrix(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) throws UnauthorizedException, BadRequestException;

    UserSegregationAnalyticsDTO getUserAnalytics(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) throws UnauthorizedException, BadRequestException;

    List<LeadStatusAnalyticsDTO> getLeadStatusAnalytics(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) throws UnauthorizedException, BadRequestException;

    CourseSegregationResponseDTO getCourseWiseSegregation(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId, String search, int page, int size, String sortBy, String sortDirection) throws UnauthorizedException, BadRequestException;

    CourseUserSegregationResponseDTO getCourseUserWiseSegregation(UUID courseId, UUID leadSourceId, UUID boardId, UUID gradeId, String search, int page, int size, String sortBy, String sortDirection) throws UnauthorizedException, BadRequestException;

    UserAllocationSummaryDTO getUserAllocationSummary(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException;

    UserAllocationUsersResponseDTO getUserAllocationUsers(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException;
}
