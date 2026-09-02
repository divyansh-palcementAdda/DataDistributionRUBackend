package com.app.datadistribution.repository;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadSourcePageResponse;
import com.app.datadistribution.service.dto.UserDataScope;

public interface LeadSourceRepositoryCustom {

    LeadSourcePageResponse fetchLeadSourcesWithLeadStats(PageRequestDTO pageRequest, String status, UserDataScope dataScope);
}
