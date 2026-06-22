package com.app.datadistribution.mapper;

import com.app.datadistribution.dto.course.*;
import com.app.datadistribution.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @org.mapstruct.Builder(disableBuilder = true))
public interface CourseMapper {

    // --- CourseType ---
    CourseTypeResponseDTO toDto(CourseType courseType);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    CourseType toEntity(CourseTypeRequestDTO dto);

    // --- Course ---
    @Mapping(source = "courseType", target = "courseType")
    CourseResponseDTO toDto(Course course);

    CourseSummaryDTO toSummaryDto(Course course);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "courseType", ignore = true)
    @Mapping(target = "leads", ignore = true)
    Course toEntity(CourseRequestDTO dto);
}
