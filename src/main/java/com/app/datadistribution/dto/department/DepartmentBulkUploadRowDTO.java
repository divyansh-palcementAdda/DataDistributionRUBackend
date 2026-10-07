package com.app.datadistribution.dto.department;

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
public class DepartmentBulkUploadRowDTO {
    private int rowNumber;
    private String name;
    private String code;
    private String description;
    private boolean active;
    private boolean isUpdate;
    private UUID existingId;
}
