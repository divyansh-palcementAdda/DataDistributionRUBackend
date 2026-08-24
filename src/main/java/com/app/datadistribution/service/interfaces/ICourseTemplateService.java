package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.coursetemplate.CourseTemplateRequestDTO;
import com.app.datadistribution.dto.coursetemplate.CourseTemplateResponseDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import java.util.List;
import java.util.UUID;

public interface ICourseTemplateService {
    CourseTemplateResponseDTO create(CourseTemplateRequestDTO request) throws BadRequestException;
    CourseTemplateResponseDTO update(UUID id, CourseTemplateRequestDTO request) throws BadRequestException;
    CourseTemplateResponseDTO getById(UUID id);
    List<CourseTemplateResponseDTO> getTemplatesByCourseId(UUID courseId);
    List<CourseTemplateResponseDTO> getAllTemplates();
    void deleteTemplate(UUID id);
    List<CourseTemplateResponseDTO> getTemplatesForLead(UUID leadId) throws UnauthorizedException;
    void sendTemplateToLead(UUID leadId, UUID templateId) throws UnauthorizedException, BadRequestException;
}
