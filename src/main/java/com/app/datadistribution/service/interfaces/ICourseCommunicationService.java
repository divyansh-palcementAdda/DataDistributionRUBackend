package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.communication.CourseCommunicationConfigDTO;
import com.app.datadistribution.dto.communication.InfoPanelResponseDTO;
import com.app.datadistribution.dto.communication.SendCommunicationRequestDTO;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import java.util.UUID;

public interface ICourseCommunicationService {
    CourseCommunicationConfigDTO getCommunicationConfig(UUID courseId);
    CourseCommunicationConfigDTO updateCommunicationConfig(UUID courseId, CourseCommunicationConfigDTO configDTO) throws BadRequestException;
    InfoPanelResponseDTO getInfoPanelForLead(UUID leadId, UUID courseId);
    void sendCourseEmail(UUID leadId, SendCommunicationRequestDTO request) throws BadRequestException, UnauthorizedException;
    void sendCourseWhatsApp(UUID leadId, SendCommunicationRequestDTO request) throws BadRequestException, UnauthorizedException;
}
