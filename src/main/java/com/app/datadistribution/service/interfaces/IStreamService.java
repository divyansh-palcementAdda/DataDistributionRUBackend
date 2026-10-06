package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.stream.StreamPageResponse;
import com.app.datadistribution.dto.stream.StreamRequest;
import com.app.datadistribution.dto.stream.StreamResponse;
import java.util.UUID;

public interface IStreamService {
    StreamResponse create(StreamRequest request);
    StreamResponse update(UUID id, StreamRequest request);
    StreamResponse getById(UUID id);
    StreamPageResponse getAll(PageRequestDTO request);
    StreamPageResponse getAll(PageRequestDTO request, String status);
    void delete(UUID id);
    StreamResponse toggleActive(UUID id);
}
