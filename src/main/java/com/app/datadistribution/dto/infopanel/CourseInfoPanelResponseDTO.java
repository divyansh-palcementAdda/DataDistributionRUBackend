package com.app.datadistribution.dto.infopanel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
public class CourseInfoPanelResponseDTO {
    private UUID id;
    private CourseSummaryDTO course;
    private String academicSession;
    private String school;
    private String courseName;
    private String courseFee;
    private String duration;
    private String eligibility;
    private String jobOpportunities;
    private String hostelFee;
    private String courseDetails;
    private String courseSpecialities;
    private String renaissanceUniversityUsps;
    private String howWeAreDifferent;
    private String callerGuidance;
    private boolean active;
    private List<CompetitorResponseDTO> competitors;
    private Map<String, Boolean> fieldPermissions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
