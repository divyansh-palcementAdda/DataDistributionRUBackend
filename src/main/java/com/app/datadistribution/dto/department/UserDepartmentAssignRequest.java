package com.app.datadistribution.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Bulk user department assignment request payload")
public class UserDepartmentAssignRequest {

    @Schema(description = "List of department UUIDs to assign to the user")
    private List<UUID> departmentIds;
}
