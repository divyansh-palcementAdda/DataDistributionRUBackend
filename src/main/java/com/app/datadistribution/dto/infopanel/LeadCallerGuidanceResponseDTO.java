package com.app.datadistribution.dto.infopanel;

import java.util.List;
import java.util.UUID;

import com.app.datadistribution.dto.course.CourseSummaryDTO;

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
public class LeadCallerGuidanceResponseDTO {
    private UUID leadId;
    private String leadCode;
    private String leadFullName;
    private CourseSummaryDTO primaryCourse;
    private List<CourseSummaryDTO> interestedCourses;
    private UUID activeCourseId;
    private CourseInfoPanelResponseDTO infoPanel;
}
