package com.app.datadistribution.dto.infopanel;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CourseInfoPanelRequestDTO {

    @NotNull(message = "Course ID is required")
    private UUID courseId;

    @NotBlank(message = "Academic session is required")
    @Builder.Default
    private String academicSession = "2026-27";

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

    @Builder.Default
    private Boolean active = true;

    private List<CompetitorRequestDTO> competitors;
}
