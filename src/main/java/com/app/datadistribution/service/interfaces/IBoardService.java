package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.BoardPageResponse;
import com.app.datadistribution.dto.lead.BoardRequest;
import com.app.datadistribution.dto.lead.BoardResponse;
import java.util.UUID;

public interface IBoardService {
    BoardResponse create(BoardRequest request);
    BoardResponse update(UUID id, BoardRequest request);
    BoardResponse getById(UUID id);
    BoardPageResponse getAll(PageRequestDTO request);
    BoardPageResponse getAll(PageRequestDTO request, String status);
    void delete(UUID id);
    BoardResponse toggleActive(UUID id);
}
