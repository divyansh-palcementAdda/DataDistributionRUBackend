package com.app.datadistribution.dto.course;

import java.util.ArrayList;
import java.util.List;
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
public class CourseBulkUploadPreviewResponseDTO {
    private boolean success;
    private String message;
    private int totalRows;
    private int validRows;
    private int errorRows;
    private int newCourses;
    private int updateCourses;
    private boolean canImport;
    private boolean errorFileAvailable;
    private String importId;

    @Builder.Default
    private List<CourseBulkUploadRowDTO> rows = new ArrayList<>();

    @Builder.Default
    private List<CourseBulkUploadRowErrorDTO> errors = new ArrayList<>();
}
