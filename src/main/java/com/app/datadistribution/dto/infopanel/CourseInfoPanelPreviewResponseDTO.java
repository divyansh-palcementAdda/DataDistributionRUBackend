package com.app.datadistribution.dto.infopanel;

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
public class CourseInfoPanelPreviewResponseDTO {

    private boolean success;
    private String message;
    private int totalRows;
    private int validRows;
    private int errorRows;
    private int newRecords;
    private int recordsToUpdate;

    private int sheet1RowCount;
    private int sheet2RowCount;

    private boolean canImport;
    private boolean errorFileAvailable;
    private String importId;

    @Builder.Default
    private List<CourseInfoPanelBulkUploadRowErrorDTO> errors = new ArrayList<>();
}
