package com.app.datadistribution.repository;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.BoardPageResponse;
import com.app.datadistribution.service.dto.UserDataScope;

public interface BoardRepositoryCustom {

    BoardPageResponse fetchBoardsWithLeadStats(PageRequestDTO pageRequest, String status, UserDataScope dataScope);
}
