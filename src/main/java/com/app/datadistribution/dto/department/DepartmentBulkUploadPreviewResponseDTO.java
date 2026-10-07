package com.app.datadistribution.dto.department;

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
public class DepartmentBulkUploadPreviewResponseDTO {
    private boolean success;
    private String message;
    private int totalRows;
    private int validRows;
    private int errorRows;
    private int newDepartments;
    private int updateDepartments;
    private boolean canImport;
    private boolean errorFileAvailable;
    private String importId;

    @Builder.Default
    private List<DepartmentBulkUploadRowDTO> rows = new ArrayList<>();

    @Builder.Default
    private List<DepartmentBulkUploadRowErrorDTO> errors = new ArrayList<>();
}
