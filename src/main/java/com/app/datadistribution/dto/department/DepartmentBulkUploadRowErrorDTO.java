package com.app.datadistribution.dto.department;

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
public class DepartmentBulkUploadRowErrorDTO {
    private int rowNumber;
    private String departmentName;
    private String departmentCode;
    private String field;
    private String errorCode;
    private String errorMessage;
}
