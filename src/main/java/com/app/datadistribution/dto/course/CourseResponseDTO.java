package com.app.datadistribution.dto.course;

import com.app.datadistribution.enums.Status;
import java.time.LocalDateTime;
import java.util.UUID;
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
public class CourseResponseDTO {
    private UUID id;
    private String courseName;
    private String courseCode;
    private String description;
    private Integer duration;
    private String durationUnit;
    private Double fees;
    private CourseTypeResponseDTO courseType;
    private Status status;
    private java.util.List<com.app.datadistribution.dto.courseimage.CourseImageDTO> images;
    private java.util.List<com.app.datadistribution.dto.courseusp.CourseUSPDTO> usps;
    private com.app.datadistribution.dto.communication.CourseCommunicationConfigDTO communicationConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
