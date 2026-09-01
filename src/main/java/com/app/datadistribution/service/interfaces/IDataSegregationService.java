package com.app.datadistribution.service.interfaces;

import java.util.List;
import java.util.UUID;

import com.app.datadistribution.dto.segregation.CourseTypeSegregationDTO;
import com.app.datadistribution.dto.segregation.LeadStatusAnalyticsDTO;
import com.app.datadistribution.dto.segregation.SegregationMatrixResponseDTO;
import com.app.datadistribution.dto.segregation.UserSegregationAnalyticsDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

public interface IDataSegregationService {

    List<CourseTypeSegregationDTO> getCourseTypesSummary() throws UnauthorizedException, BadRequestException;

    SegregationMatrixResponseDTO getSegregationMatrix(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) throws UnauthorizedException, BadRequestException;

    UserSegregationAnalyticsDTO getUserAnalytics(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) throws UnauthorizedException, BadRequestException;

    List<LeadStatusAnalyticsDTO> getLeadStatusAnalytics(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) throws UnauthorizedException, BadRequestException;
}
