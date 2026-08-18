package com.app.datadistribution.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Concise department summary reference DTO")
public class DepartmentSummaryDTO {

    @Schema(description = "Department unique identifier (UUID)")
    private UUID id;

    @Schema(description = "Department name", example = "Admissions")
    private String name;

    @Schema(description = "Department unique code", example = "ADM")
    private String code;
}
