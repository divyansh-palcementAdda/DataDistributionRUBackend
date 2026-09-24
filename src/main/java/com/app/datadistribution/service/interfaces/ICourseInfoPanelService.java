package com.app.datadistribution.service.interfaces;

import java.util.List;
import java.util.UUID;

import com.app.datadistribution.dto.infopanel.CompetitorRequestDTO;
import com.app.datadistribution.dto.infopanel.CompetitorResponseDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelRequestDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelResponseDTO;
import com.app.datadistribution.dto.infopanel.InfoPanelPermissionMatrixDTO;
import com.app.datadistribution.dto.infopanel.LeadCallerGuidanceResponseDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

public interface ICourseInfoPanelService {

    CourseInfoPanelResponseDTO getInfoPanelByCourseId(UUID courseId, String academicSession);

    LeadCallerGuidanceResponseDTO getCallerGuidanceForLead(UUID leadId, UUID courseId);

    CourseInfoPanelResponseDTO getInfoPanelById(UUID id);

    CourseInfoPanelResponseDTO createInfoPanel(CourseInfoPanelRequestDTO request) throws BadRequestException, UnauthorizedException;

    CourseInfoPanelResponseDTO updateInfoPanel(UUID id, CourseInfoPanelRequestDTO request) throws UnauthorizedException;

    void deleteInfoPanel(UUID id);

    CompetitorResponseDTO addCompetitor(UUID infoPanelId, CompetitorRequestDTO request) throws BadRequestException;

    CompetitorResponseDTO updateCompetitor(UUID infoPanelId, UUID competitorId, CompetitorRequestDTO request);

    void deleteCompetitor(UUID infoPanelId, UUID competitorId);

    void reorderCompetitors(UUID infoPanelId, List<UUID> competitorIds);

    InfoPanelPermissionMatrixDTO getPermissionMatrix();
}
