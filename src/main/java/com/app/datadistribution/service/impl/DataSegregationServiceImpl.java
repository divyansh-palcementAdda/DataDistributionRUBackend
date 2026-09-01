package com.app.datadistribution.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.segregation.CourseTypeSegregationDTO;
import com.app.datadistribution.dto.segregation.LeadStatusAnalyticsDTO;
import com.app.datadistribution.dto.segregation.SegregationMatrixResponseDTO;
import com.app.datadistribution.dto.segregation.UserSegregationAnalyticsDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.BoardRepository;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.repository.DataSegregationRepository;
import com.app.datadistribution.repository.GradeRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.service.dto.UserDataScope;
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
    private final CourseTypeRepository courseTypeRepository;
    private final LeadSourceRepository leadSourceRepository;
    private final BoardRepository boardRepository;
    private final GradeRepository gradeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CourseTypeSegregationDTO> getCourseTypesSummary() throws UnauthorizedException, BadRequestException {
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return segregationRepository.fetchCourseTypeSummary(dataScope);
    }

    @Override
    @Transactional(readOnly = true)
    public SegregationMatrixResponseDTO getSegregationMatrix(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId)
            throws UnauthorizedException, BadRequestException {
        if (courseTypeId == null) {
            throw new BadRequestException("courseTypeId is required for data segregation matrix.");
        }

        validateEntities(courseTypeId, leadSourceId, boardId, gradeId);

        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return segregationRepository.fetchSegregationMatrix(courseTypeId, leadSourceId, boardId, gradeId, dataScope);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSegregationAnalyticsDTO getUserAnalytics(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId)
            throws UnauthorizedException, BadRequestException {
        if (courseTypeId == null) {
            throw new BadRequestException("courseTypeId is required for user analytics.");
        }
        if (leadSourceId == null) {
            throw new BadRequestException("leadSourceId is required for user analytics.");
        }

        validateEntities(courseTypeId, leadSourceId, boardId, gradeId);

        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return segregationRepository.fetchUserAnalytics(courseTypeId, leadSourceId, boardId, gradeId, dataScope);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadStatusAnalyticsDTO> getLeadStatusAnalytics(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId)
            throws UnauthorizedException, BadRequestException {
        if (courseTypeId == null) {
            throw new BadRequestException("courseTypeId is required for lead status analytics.");
        }
        if (leadSourceId == null) {
            throw new BadRequestException("leadSourceId is required for lead status analytics.");
        }

        validateEntities(courseTypeId, leadSourceId, boardId, gradeId);

        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        return segregationRepository.fetchLeadStatusAnalytics(courseTypeId, leadSourceId, boardId, gradeId, dataScope);
    }

    private void validateEntities(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) {
        if (courseTypeId != null && !courseTypeRepository.existsById(courseTypeId)) {
            throw new ResourcesNotFoundException("Course Type not found with id: " + courseTypeId);
        }
        if (leadSourceId != null && !leadSourceRepository.existsById(leadSourceId)) {
            throw new ResourcesNotFoundException("Lead Source not found with id: " + leadSourceId);
        }
        if (boardId != null && !boardRepository.existsById(boardId)) {
            throw new ResourcesNotFoundException("Board not found with id: " + boardId);
        }
        if (gradeId != null && !gradeRepository.existsById(gradeId)) {
            throw new ResourcesNotFoundException("Grade not found with id: " + gradeId);
        }
    }
}
