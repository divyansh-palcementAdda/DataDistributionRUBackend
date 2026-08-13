package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadStatusPageResponse;
import com.app.datadistribution.dto.lead.LeadStatusRequest;
import com.app.datadistribution.dto.lead.LeadStatusResponse;
import java.util.UUID;

public interface ILeadStatusService {
    LeadStatusResponse create(LeadStatusRequest request);
    LeadStatusResponse update(UUID id, LeadStatusRequest request);
    LeadStatusResponse getById(UUID id);
    LeadStatusPageResponse getAll(PageRequestDTO request);
    LeadStatusPageResponse getAll(PageRequestDTO request, String status);
    void delete(UUID id);
    LeadStatusResponse toggleActive(UUID id);
}
