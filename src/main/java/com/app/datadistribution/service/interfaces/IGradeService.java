package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.GradePageResponse;
import com.app.datadistribution.dto.lead.GradeRequest;
import com.app.datadistribution.dto.lead.GradeResponse;
import java.util.UUID;

public interface IGradeService {
    GradeResponse create(GradeRequest request);
    GradeResponse update(UUID id, GradeRequest request);
    GradeResponse getById(UUID id);
    GradePageResponse getAll(PageRequestDTO request);
    GradePageResponse getAll(PageRequestDTO request, String status);
    void delete(UUID id);
    GradeResponse toggleActive(UUID id);
}
