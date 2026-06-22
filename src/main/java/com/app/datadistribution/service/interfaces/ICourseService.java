package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.course.CoursePagedResponseDTO;
import com.app.datadistribution.dto.course.CourseRequestDTO;
import com.app.datadistribution.dto.course.CourseResponseDTO;
import java.util.List;
import java.util.UUID;

public interface ICourseService {
    CourseResponseDTO create(CourseRequestDTO request);
    CourseResponseDTO update(UUID id, CourseRequestDTO request);
    CourseResponseDTO getById(UUID id);
    CoursePagedResponseDTO getAll(PageRequestDTO pageRequest, UUID courseTypeId);
    List<CourseResponseDTO> getAllActive();
    void delete(UUID id);
    CourseResponseDTO toggleActive(UUID id);
}
