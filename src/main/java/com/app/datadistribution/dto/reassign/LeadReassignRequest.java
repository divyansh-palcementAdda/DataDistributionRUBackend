package com.app.datadistribution.dto.reassign;

import jakarta.validation.constraints.NotEmpty;
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
public class LeadReassignRequest {

    @NotNull(message = "Source user ID is required")
    private UUID sourceUserId;

    @NotEmpty(message = "Assignments list cannot be empty")
    private List<LeadReassignItemDTO> assignments;

    private String reason;
    private boolean reassignRelatedPendingFollowUps; // Default false. If true, transfers pending uncompleted follow-ups for reassigned leads
}
