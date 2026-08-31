package com.app.datadistribution.service.interfaces;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadPageResponse;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.lead.LeadSourceStatsResponse;
import com.app.datadistribution.dto.lead.LeadStatusChangeRequest;
import com.app.datadistribution.dto.lead.LeadStatusHistoryResponse;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

public interface ILeadService {
    LeadResponse create(LeadRequest request) throws BadRequestException, UnauthorizedException;
    LeadResponse update(UUID id, LeadRequest request) throws BadRequestException, UnauthorizedException;
    LeadResponse getById(UUID id) throws UnauthorizedException, BadRequestException;
    LeadPageResponse getAllLeads(
            PageRequestDTO pageRequest,
            List<UUID> leadSourceIds,
            UUID courseId,
            List<UUID> interestedCourseIds,
            UUID registeredCourseId,
            UUID courseTypeId,
            List<UUID> courseTypeIds,
            Boolean withoutCourse,
            UUID statusId,
            List<UUID> statusIds,
            UUID boardId,
            List<UUID> boardIds,
            UUID gradeId,
            List<UUID> gradeIds,
            List<UUID> departmentIds,
            List<UUID> assignedUserIds,
            Boolean allotted,
            Boolean availed,
            UUID availedByUserId,
            List<UUID> availedByUserIds,
            java.time.LocalDate availedFrom,
            java.time.LocalDate availedTo,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            java.time.LocalDate updatedFrom,
            java.time.LocalDate updatedTo,
            UUID leadStatusHistoryId,
            List<UUID> leadStatusHistoryIds,
            String leadStatusHistory
    ) throws UnauthorizedException, BadRequestException;
    LeadPageResponse getAllLeads(PageRequestDTO pageRequest, List<UUID> leadSourceIds, UUID courseId, List<UUID> interestedCourseIds, UUID registeredCourseId, UUID courseTypeId, List<UUID> courseTypeIds, Boolean withoutCourse, UUID statusId, List<UUID> statusIds, UUID boardId, List<UUID> boardIds, UUID gradeId, List<UUID> gradeIds, List<UUID> departmentIds, List<UUID> assignedUserIds, Boolean allotted, Boolean availed, UUID availedByUserId, List<UUID> availedByUserIds, java.time.LocalDate availedFrom, java.time.LocalDate availedTo, java.time.LocalDate startDate, java.time.LocalDate endDate, java.time.LocalDate updatedFrom, java.time.LocalDate updatedTo) throws UnauthorizedException, BadRequestException;
    LeadPageResponse getAllLeads(PageRequestDTO pageRequest, List<UUID> leadSourceIds, UUID courseId, List<UUID> interestedCourseIds, UUID registeredCourseId, UUID courseTypeId, Boolean withoutCourse, UUID statusId, List<UUID> statusIds, UUID boardId, List<UUID> boardIds, UUID gradeId, List<UUID> gradeIds, Boolean availed) throws UnauthorizedException, BadRequestException;
    LeadPageResponse getAllLeads(PageRequestDTO pageRequest, List<UUID> leadSourceIds, UUID courseId, List<UUID> interestedCourseIds, UUID registeredCourseId, UUID courseTypeId, Boolean withoutCourse, UUID statusId, List<UUID> statusIds, UUID boardId, List<UUID> boardIds, UUID gradeId, List<UUID> gradeIds) throws UnauthorizedException, BadRequestException;
    LeadResponse addInterestedCourses(UUID leadId, List<UUID> courseIds) throws UnauthorizedException, BadRequestException;
    LeadResponse removeInterestedCourse(UUID leadId, UUID courseId) throws UnauthorizedException, BadRequestException;
    LeadResponse updateLeadCourses(UUID leadId, com.app.datadistribution.dto.lead.LeadCoursesUpdateRequest request) throws UnauthorizedException, BadRequestException;
    LeadResponse registerCourse(UUID leadId, UUID courseId) throws UnauthorizedException, BadRequestException;
    void deleteLead(UUID id) throws UnauthorizedException, BadRequestException;
    LeadResponse changeStatus(UUID id, LeadStatusChangeRequest request) throws BadRequestException, UnauthorizedException;
    com.app.datadistribution.dto.lead.LeadAvailedResponse markLeadAsAvailed(UUID leadId) throws UnauthorizedException, BadRequestException;
    List<LeadStatusHistoryResponse> getStatusHistoryByLeadId(UUID leadId) throws UnauthorizedException, BadRequestException;
    com.app.datadistribution.dto.lead.LeadStatusHistoryPageResponse getStatusHistoryByLeadId(UUID leadId, PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException;
    List<LeadSourceStatsResponse> getSourceWiseStats() throws UnauthorizedException, BadRequestException;
    Map<String, Long> getStatusWiseStats() throws UnauthorizedException, BadRequestException;
}
