package com.app.datadistribution.dto.dashboard;

import jakarta.validation.constraints.NotNull;
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
public class CardRoleAssignRequest {

    @NotNull(message = "Role IDs list is required")
    private List<UUID> roleIds;
}
