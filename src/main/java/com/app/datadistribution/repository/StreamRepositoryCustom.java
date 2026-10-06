package com.app.datadistribution.repository;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.stream.StreamPageResponse;
import com.app.datadistribution.service.dto.UserDataScope;

public interface StreamRepositoryCustom {

    StreamPageResponse fetchStreamsWithLeadStats(PageRequestDTO pageRequest, String status, UserDataScope dataScope);
}
