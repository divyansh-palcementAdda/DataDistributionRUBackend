package com.app.datadistribution.repository;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.common.PageResponseDTO;
import com.app.datadistribution.dto.course.CourseTypeResponseDTO;
import com.app.datadistribution.service.dto.UserDataScope;

public interface CourseTypeRepositoryCustom {

    PageResponseDTO<CourseTypeResponseDTO> fetchCourseTypesWithLeadStats(PageRequestDTO pageRequest, UserDataScope dataScope);
}
