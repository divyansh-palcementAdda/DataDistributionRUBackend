package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadSourcePageResponse;
import com.app.datadistribution.dto.lead.LeadSourceRequest;
import com.app.datadistribution.dto.lead.LeadSourceResponse;
import java.util.UUID;

public interface ILeadSourceService {
    LeadSourceResponse create(LeadSourceRequest request);
    LeadSourceResponse update(UUID id, LeadSourceRequest request);
    LeadSourceResponse getById(UUID id);
    LeadSourcePageResponse getAll(PageRequestDTO request);
    void delete(UUID id);
    LeadSourceResponse toggleActive(UUID id);
}
