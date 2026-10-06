package com.app.datadistribution.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest;
import com.app.datadistribution.dto.segregation.UserAllocationRowDTO;
import com.app.datadistribution.dto.segregation.UserAllocationSummaryDTO;
import com.app.datadistribution.dto.segregation.UserAllocationUsersResponseDTO;
import com.app.datadistribution.dto.segregation.CourseSegregationResponseDTO;
import com.app.datadistribution.dto.segregation.CourseUserSegregationResponseDTO;
import com.app.datadistribution.dto.segregation.CourseTypeSegregationDTO;
import com.app.datadistribution.dto.segregation.DataSegregationCapabilitiesDTO;
import com.app.datadistribution.dto.segregation.LeadStatusAnalyticsDTO;
import com.app.datadistribution.dto.segregation.SegregationMatrixResponseDTO;
import com.app.datadistribution.dto.segregation.UserSegregationAnalyticsDTO;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.BoardRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.repository.DataSegregationRepository;
import com.app.datadistribution.repository.GradeRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.StreamRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.interfaces.IDataSegregationPermissionService;
import com.app.datadistribution.service.interfaces.IDataSegregationService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSegregationServiceImpl implements IDataSegregationService {

    private final DataSegregationRepository segregationRepository;
    private final IUserDataScopeService dataScopeService;
    private final IDataSegregationPermissionService segregationPermissionService;
    private final CourseTypeRepository courseTypeRepository;
    private final CourseRepository courseRepository;
    private final LeadSourceRepository leadSourceRepository;
    private final BoardRepository boardRepository;
    private final StreamRepository streamRepository;
    private final GradeRepository gradeRepository;

    @Override
    public DataSegregationCapabilitiesDTO getCapabilities() {
        return segregationPermissionService.getCapabilities();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseTypeSegregationDTO> getCourseTypesSummary() throws UnauthorizedException, BadRequestException {
        segregationPermissionService.validateCourseTypeAccess();
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return segregationRepository.fetchCourseTypeSummary(dataScope);
    }

    @Override
    @Transactional(readOnly = true)
    public SegregationMatrixResponseDTO getSegregationMatrix(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId)
            throws UnauthorizedException, BadRequestException {
        return getSegregationMatrix(courseTypeId, leadSourceId, boardId, null, gradeId);
    }

    @Override
    @Transactional(readOnly = true)
    public SegregationMatrixResponseDTO getSegregationMatrix(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID streamId, UUID gradeId)
            throws UnauthorizedException, BadRequestException {
        if (courseTypeId == null) {
            throw new BadRequestException("courseTypeId is required for data segregation matrix.");
        }

        segregationPermissionService.validateMatrixAccess(courseTypeId, leadSourceId, boardId, streamId, gradeId);
        validateEntities(courseTypeId, leadSourceId, boardId, streamId, gradeId);

        DataSegregationCapabilitiesDTO capabilities = segregationPermissionService.getCapabilities();
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return segregationRepository.fetchSegregationMatrix(courseTypeId, leadSourceId, boardId, streamId, gradeId, dataScope, capabilities);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSegregationAnalyticsDTO getUserAnalytics(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId)
            throws UnauthorizedException, BadRequestException {
        return getUserAnalytics(courseTypeId, leadSourceId, boardId, null, gradeId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSegregationAnalyticsDTO getUserAnalytics(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID streamId, UUID gradeId)
            throws UnauthorizedException, BadRequestException {
        if (courseTypeId == null) {
            throw new BadRequestException("courseTypeId is required for user analytics.");
        }
        if (leadSourceId == null) {
            throw new BadRequestException("leadSourceId is required for user analytics.");
        }

        segregationPermissionService.validateUserAnalyticsAccess(courseTypeId, leadSourceId, boardId, streamId, gradeId);
        validateEntities(courseTypeId, leadSourceId, boardId, streamId, gradeId);

        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return segregationRepository.fetchUserAnalytics(courseTypeId, leadSourceId, boardId, streamId, gradeId, dataScope);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadStatusAnalyticsDTO> getLeadStatusAnalytics(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId)
            throws UnauthorizedException, BadRequestException {
        return getLeadStatusAnalytics(courseTypeId, leadSourceId, boardId, null, gradeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadStatusAnalyticsDTO> getLeadStatusAnalytics(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID streamId, UUID gradeId)
            throws UnauthorizedException, BadRequestException {
        if (courseTypeId == null) {
            throw new BadRequestException("courseTypeId is required for lead status analytics.");
        }
        if (leadSourceId == null) {
            throw new BadRequestException("leadSourceId is required for lead status analytics.");
        }

        segregationPermissionService.validateLeadStatusAnalyticsAccess(courseTypeId, leadSourceId, boardId, streamId, gradeId);
        validateEntities(courseTypeId, leadSourceId, boardId, streamId, gradeId);

        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return segregationRepository.fetchLeadStatusAnalytics(courseTypeId, leadSourceId, boardId, streamId, gradeId, dataScope);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseSegregationResponseDTO getCourseWiseSegregation(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId,
                                                                String search, int page, int size, String sortBy, String sortDirection)
            throws UnauthorizedException, BadRequestException {
        return getCourseWiseSegregation(courseTypeId, leadSourceId, boardId, null, gradeId, search, page, size, sortBy, sortDirection);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseSegregationResponseDTO getCourseWiseSegregation(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID streamId, UUID gradeId,
                                                                String search, int page, int size, String sortBy, String sortDirection)
            throws UnauthorizedException, BadRequestException {
        if (courseTypeId == null) {
            throw new BadRequestException("courseTypeId is required for course-wise segregation.");
        }

        segregationPermissionService.validateCourseAccess(courseTypeId, leadSourceId, boardId, streamId, gradeId);
        validateEntities(courseTypeId, leadSourceId, boardId, streamId, gradeId);

        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return segregationRepository.fetchCourseWiseSegregation(courseTypeId, leadSourceId, boardId, streamId, gradeId, search, page, size, sortBy, sortDirection, dataScope);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseUserSegregationResponseDTO getCourseUserWiseSegregation(UUID courseId, UUID leadSourceId, UUID boardId, UUID gradeId,
                                                                        String search, int page, int size, String sortBy, String sortDirection)
            throws UnauthorizedException, BadRequestException {
        return getCourseUserWiseSegregation(courseId, leadSourceId, boardId, null, gradeId, search, page, size, sortBy, sortDirection);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseUserSegregationResponseDTO getCourseUserWiseSegregation(UUID courseId, UUID leadSourceId, UUID boardId, UUID streamId, UUID gradeId,
                                                                        String search, int page, int size, String sortBy, String sortDirection)
            throws UnauthorizedException, BadRequestException {
        if (courseId == null) {
            throw new BadRequestException("courseId is required for course user-wise segregation.");
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + courseId));

        UUID courseTypeId = course.getCourseType() != null ? course.getCourseType().getId() : null;
        segregationPermissionService.validateCourseUserAccess(courseTypeId, courseId, leadSourceId, boardId, streamId, gradeId);
        validateEntities(courseTypeId, leadSourceId, boardId, streamId, gradeId);

        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return segregationRepository.fetchCourseUserWiseSegregation(courseId, leadSourceId, boardId, streamId, gradeId, search, page, size, sortBy, sortDirection, dataScope);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAllocationSummaryDTO getUserAllocationSummary(DashboardAnalyticsFilterRequest filterRequest)
            throws UnauthorizedException, BadRequestException {
        if (filterRequest == null) {
            filterRequest = new DashboardAnalyticsFilterRequest();
        }
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser(filterRequest);
        return segregationRepository.fetchUserAllocationSummary(filterRequest, dataScope);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAllocationUsersResponseDTO getUserAllocationUsers(DashboardAnalyticsFilterRequest filterRequest)
            throws UnauthorizedException, BadRequestException {
        if (filterRequest == null) {
            filterRequest = new DashboardAnalyticsFilterRequest();
        }
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser(filterRequest);
        return segregationRepository.fetchUserAllocationUsers(filterRequest, dataScope);
    }

    private void validateEntities(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) {
        validateEntities(courseTypeId, leadSourceId, boardId, null, gradeId);
    }

    private void validateEntities(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID streamId, UUID gradeId) {
        if (courseTypeId != null && !courseTypeRepository.existsById(courseTypeId)) {
            throw new ResourcesNotFoundException("Course Type not found with id: " + courseTypeId);
        }
        if (leadSourceId != null && !leadSourceRepository.existsById(leadSourceId)) {
            throw new ResourcesNotFoundException("Lead Source not found with id: " + leadSourceId);
        }
        if (boardId != null && !boardRepository.existsById(boardId)) {
            throw new ResourcesNotFoundException("Board not found with id: " + boardId);
        }
        if (streamId != null && !streamRepository.existsById(streamId)) {
            throw new ResourcesNotFoundException("Stream not found with id: " + streamId);
        }
        if (gradeId != null && !gradeRepository.existsById(gradeId)) {
            throw new ResourcesNotFoundException("Grade not found with id: " + gradeId);
        }
    }
}
