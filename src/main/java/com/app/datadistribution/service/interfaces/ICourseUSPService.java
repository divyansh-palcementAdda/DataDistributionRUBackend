package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.courseusp.CourseUSPDTO;
import com.app.datadistribution.exception.BadRequestException;
import java.util.List;
import java.util.UUID;

public interface ICourseUSPService {
    CourseUSPDTO createUSP(UUID courseId, String content, Integer displayOrder, Boolean active) throws BadRequestException;
    CourseUSPDTO updateUSP(UUID uspId, String content, Integer displayOrder, Boolean active);
    List<CourseUSPDTO> getUSPsByCourseId(UUID courseId, boolean activeOnly);
    CourseUSPDTO getById(UUID uspId);
    void deleteUSP(UUID uspId);
}
