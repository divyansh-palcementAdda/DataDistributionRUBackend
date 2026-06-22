package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.common.PageResponseDTO;
import com.app.datadistribution.dto.course.CourseTypeRequestDTO;
import com.app.datadistribution.dto.course.CourseTypeResponseDTO;
import java.util.List;
import java.util.UUID;

public interface ICourseTypeService {
    CourseTypeResponseDTO create(CourseTypeRequestDTO request);
    CourseTypeResponseDTO update(UUID id, CourseTypeRequestDTO request);
    CourseTypeResponseDTO getById(UUID id);
    PageResponseDTO<CourseTypeResponseDTO> getAll(PageRequestDTO pageRequest);
    List<CourseTypeResponseDTO> getAllActive();
    void delete(UUID id);
    CourseTypeResponseDTO toggleActive(UUID id);
}
