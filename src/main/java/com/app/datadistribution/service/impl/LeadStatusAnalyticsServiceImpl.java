package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.analytics.CourseStatusAnalyticsResponseDTO;
import com.app.datadistribution.dto.analytics.CourseUserStatusAnalyticsResponseDTO;
import com.app.datadistribution.dto.analytics.LeadStatusAnalyticsFilterRequest;
import com.app.datadistribution.dto.analytics.UserStatusAnalyticsResponseDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.LeadStatusAnalyticsRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.interfaces.ILeadStatusAnalyticsService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadStatusAnalyticsServiceImpl implements ILeadStatusAnalyticsService {

    private final LeadStatusAnalyticsRepository analyticsRepository;
    private final IUserDataScopeService dataScopeService;

    @Override
    @Transactional(readOnly = true)
    public CourseStatusAnalyticsResponseDTO getCourseWiseStatusCounts(LeadStatusAnalyticsFilterRequest filter)
            throws UnauthorizedException, BadRequestException {
        if (filter == null) {
            filter = new LeadStatusAnalyticsFilterRequest();
        }
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return analyticsRepository.fetchCourseWiseAnalytics(filter, dataScope);
    }

    @Override
    @Transactional(readOnly = true)
    public UserStatusAnalyticsResponseDTO getUserWiseStatusCounts(LeadStatusAnalyticsFilterRequest filter)
            throws UnauthorizedException, BadRequestException {
        if (filter == null) {
            filter = new LeadStatusAnalyticsFilterRequest();
        }
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return analyticsRepository.fetchUserWiseAnalytics(filter, dataScope);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseUserStatusAnalyticsResponseDTO getCourseUserStatusAnalytics(LeadStatusAnalyticsFilterRequest filter)
            throws UnauthorizedException, BadRequestException {
        if (filter == null) {
            filter = new LeadStatusAnalyticsFilterRequest();
        }
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return analyticsRepository.fetchCourseUserWiseAnalytics(filter, dataScope);
    }
}
