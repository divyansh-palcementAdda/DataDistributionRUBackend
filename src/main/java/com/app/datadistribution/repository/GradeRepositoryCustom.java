package com.app.datadistribution.repository;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.GradePageResponse;
import com.app.datadistribution.service.dto.UserDataScope;

public interface GradeRepositoryCustom {

    GradePageResponse fetchGradesWithLeadStats(PageRequestDTO pageRequest, String status, UserDataScope dataScope);
}
