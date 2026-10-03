package com.app.datadistribution.dto.course;

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
public class CourseBulkUploadRowErrorDTO {
    private int rowNumber;
    private String courseName;
    private String courseCode;
    private String field;
    private String errorCode;
    private String errorMessage;
}
