package com.app.datadistribution.service.dto;

import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.entity.Program;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadAcademicResolutionResult {

    private Course course;

    @Builder.Default
    private Set<Course> interestedCourses = new HashSet<>();

    @Builder.Default
    private Set<Program> programs = new HashSet<>();

    private Program program;

    private CourseType courseType;

    // Source provenance tracking for audit and logs: "EXCEL", "USER", "COURSE", null
    private String courseSource;
    private String programSource;
    private String courseTypeSource;
}
