package com.app.datadistribution.dto.infopanel;

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
public class CourseInfoPanelBulkUploadRowErrorDTO {
    private String sheetName;
    private int rowNumber;
    private String courseName;
    private String collegeName;
    private String errorCode;
    private String message;
    @Builder.Default
    private String status = "ERROR";
}
