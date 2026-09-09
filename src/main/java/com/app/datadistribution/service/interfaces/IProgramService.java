package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.course.CourseResponseDTO;
import com.app.datadistribution.dto.program.ProgramCourseMappingRequestDTO;
import com.app.datadistribution.dto.program.ProgramPagedResponseDTO;
import com.app.datadistribution.dto.program.ProgramRequestDTO;
import com.app.datadistribution.dto.program.ProgramResponseDTO;
import com.app.datadistribution.dto.program.ProgramSummaryDTO;

import java.util.List;
import java.util.UUID;

public interface IProgramService {
    ProgramResponseDTO create(ProgramRequestDTO request);
    ProgramResponseDTO update(UUID id, ProgramRequestDTO request);
    ProgramResponseDTO getById(UUID id);
    ProgramPagedResponseDTO getAll(PageRequestDTO pageRequest);
    List<ProgramSummaryDTO> getAllActive();
    void delete(UUID id);
    ProgramResponseDTO toggleActive(UUID id);
    ProgramResponseDTO mapCoursesToProgram(UUID programId, ProgramCourseMappingRequestDTO request);
    List<CourseResponseDTO> getCoursesByProgramId(UUID programId);
}
