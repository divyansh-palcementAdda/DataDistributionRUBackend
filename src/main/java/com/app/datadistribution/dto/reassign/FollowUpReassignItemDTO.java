package com.app.datadistribution.dto.reassign;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpReassignItemDTO {

    @NotNull(message = "Target user ID is required")
    private UUID targetUserId;

    // Either list explicit followUpIds OR specify count for automated distribution
    private List<UUID> followUpIds;
    private Integer count;
}
