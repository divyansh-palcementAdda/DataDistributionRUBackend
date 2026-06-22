package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.*;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ILeadService {
    LeadResponse create(LeadRequest request) throws BadRequestException, UnauthorizedException;
    LeadResponse update(UUID id, LeadRequest request);
    LeadResponse getById(UUID id);
    LeadPageResponse getAllLeads(PageRequestDTO pageRequest, UUID sourceId, UUID courseId, Boolean withoutCourse);
    void deleteLead(UUID id);
    LeadResponse changeStatus(UUID id, LeadStatusChangeRequest request) throws BadRequestException, UnauthorizedException;
    List<LeadStatusHistoryResponse> getStatusHistoryByLeadId(UUID leadId);
    List<LeadSourceStatsResponse> getSourceWiseStats();
    Map<String, Long> getStatusWiseStats();
}
