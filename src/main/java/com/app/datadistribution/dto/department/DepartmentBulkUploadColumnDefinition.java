package com.app.datadistribution.dto.department;

import com.app.datadistribution.common.bulkupload.BulkUploadFieldDefinition;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DepartmentBulkUploadColumnDefinition {

    DEPARTMENT_NAME(
            "Department Name *",
            "name",
            true,
            "Text (Max 150 chars)",
            "Computer Science & Engineering",
            "Required. Unique name of the department (2 to 150 characters).",
            new HashSet<>(Arrays.asList("department name", "dept name", "department", "dept", "name", "department_name"))
    ),
    DEPARTMENT_CODE(
            "Department Code *",
            "code",
            true,
            "Text (Max 50 chars)",
            "CSE",
            "Required. Unique uppercase code of the department (2 to 50 characters).",
            new HashSet<>(Arrays.asList("department code", "dept code", "code", "department_code"))
    ),
    DESCRIPTION(
            "Description",
            "description",
            false,
            "Text",
            "Department of Computer Science & Engineering",
            "Optional. Detailed description of the department.",
            new HashSet<>(Arrays.asList("description", "desc", "about", "details"))
    ),
    STATUS(
            "Status",
            "status",
            false,
            "ACTIVE / INACTIVE",
            "ACTIVE",
            "Optional. Status of the department (Defaults to ACTIVE).",
            new HashSet<>(Arrays.asList("status", "active", "state", "is active", "department status"))
    );

    private final String headerName;
    private final String fieldKey;
    private final boolean required;
    private final String dataType;
    private final String sampleValue;
    private final String description;
    private final Set<String> aliases;

    public BulkUploadFieldDefinition toFieldDefinition() {
        return BulkUploadFieldDefinition.builder()
                .headerName(headerName)
                .fieldKey(fieldKey)
                .required(required)
                .dataType(dataType)
                .sampleValue(sampleValue)
                .description(description)
                .aliases(aliases)
                .build();
    }

    public static List<BulkUploadFieldDefinition> getAllFieldDefinitions() {
        return Arrays.stream(values())
                .map(DepartmentBulkUploadColumnDefinition::toFieldDefinition)
                .toList();
    }
}
