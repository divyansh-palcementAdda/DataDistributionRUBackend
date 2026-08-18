package com.app.datadistribution.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Department creation and update request payload")
public class DepartmentRequest {

    @NotBlank(message = "Department name is required")
    @Size(min = 2, max = 150, message = "Department name must be between 2 and 150 characters")
    @Schema(description = "Unique department name", example = "Admissions")
    private String name;

    @NotBlank(message = "Department code is required")
    @Size(min = 2, max = 50, message = "Department code must be between 2 and 50 characters")
    @Schema(description = "Unique department code", example = "ADM")
    private String code;

    @Schema(description = "Detailed description of the department", example = "Student Admissions & Enrollment Department")
    private String description;

    @Builder.Default
    @Schema(description = "Department active status flag", example = "true")
    private boolean active = true;
}
