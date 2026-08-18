package com.app.datadistribution.dto.department;

import com.app.datadistribution.dto.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
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
@Schema(description = "Detailed Department response DTO")
public class DepartmentResponse {

    @Schema(description = "Department unique identifier (UUID)")
    private UUID id;

    @Schema(description = "Department name", example = "Admissions")
    private String name;

    @Schema(description = "Department unique code", example = "ADM")
    private String code;

    @Schema(description = "Department description", example = "Student Admissions & Enrollment Department")
    private String description;

    @Schema(description = "Department active status flag", example = "true")
    private boolean active;

    @Schema(description = "Count of total users mapped to this department", example = "12")
    private long userCount;

    @Schema(description = "List of HODs mapped to this department")
    private List<UserResponse> hods;

    @Schema(description = "List of Counsellors/Users mapped to this department")
    private List<UserResponse> counsellors;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
