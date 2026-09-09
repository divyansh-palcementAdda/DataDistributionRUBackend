package com.app.datadistribution.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.datadistribution.dto.course.CourseSummaryDTO;
import com.app.datadistribution.dto.program.ProgramRequestDTO;
import com.app.datadistribution.dto.program.ProgramResponseDTO;
import com.app.datadistribution.dto.program.ProgramSummaryDTO;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Program;

@Mapper(componentModel = "spring", uses = {CourseMapper.class}, builder = @org.mapstruct.Builder(disableBuilder = true))
public interface ProgramMapper {

    ProgramSummaryDTO toSummaryDto(Program program);

    default ProgramResponseDTO toDto(Program program) {
        if (program == null) {
            return null;
        }

        List<CourseSummaryDTO> courseSummaryDtos = null;
        int count = 0;
        if (program.getCourses() != null) {
            courseSummaryDtos = program.getCourses().stream()
                    .filter(c -> !c.isDeleted())
                    .map(this::mapCourse)
                    .collect(Collectors.toList());
            count = courseSummaryDtos.size();
        }

        return ProgramResponseDTO.builder()
                .id(program.getId())
                .name(program.getName())
                .code(program.getCode())
                .description(program.getDescription())
                .status(program.getStatus())
                .courses(courseSummaryDtos)
                .totalCourses(count)
                .createdAt(program.getCreatedAt())
                .updatedAt(program.getUpdatedAt())
                .build();
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "courses", ignore = true)
    @Mapping(target = "leads", ignore = true)
    Program toEntity(ProgramRequestDTO dto);

    default CourseSummaryDTO mapCourse(Course course) {
        if (course == null) return null;
        com.app.datadistribution.dto.course.CourseTypeResponseDTO typeDto = null;
        if (course.getCourseType() != null) {
            typeDto = com.app.datadistribution.dto.course.CourseTypeResponseDTO.builder()
                    .id(course.getCourseType().getId())
                    .name(course.getCourseType().getName())
                    .description(course.getCourseType().getDescription())
                    .status(course.getCourseType().getStatus())
                    .createdAt(course.getCourseType().getCreatedAt())
                    .updatedAt(course.getCourseType().getUpdatedAt())
                    .build();
        }
        return CourseSummaryDTO.builder()
                .id(course.getId())
                .courseName(course.getCourseName())
                .courseCode(course.getCourseCode())
                .status(course.getStatus())
                .courseType(typeDto)
                .build();
    }
}
