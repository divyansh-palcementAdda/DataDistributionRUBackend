package com.app.datadistribution.dto.course;

import java.util.UUID;

import com.app.datadistribution.enums.Status;

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
public class CourseBulkUploadRowDTO {
    private int rowNumber;
    private String courseName;
    private String courseCode;
    private String courseTypeName;
    private Integer duration;
    private String durationUnit;
    private Double fees;
    private String description;
    private Status status;
    private boolean isUpdate;
    private UUID existingCourseId;
    private boolean isNewCourseType;
}
