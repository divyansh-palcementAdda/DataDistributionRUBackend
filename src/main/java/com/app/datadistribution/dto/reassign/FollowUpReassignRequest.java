package com.app.datadistribution.dto.reassign;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
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
public class FollowUpReassignRequest {

    @NotNull(message = "Source user ID is required")
    private UUID sourceUserId;

    @NotEmpty(message = "Assignments list cannot be empty")
    private List<FollowUpReassignItemDTO> assignments;

    private String reason;
    private LocalDate scheduledDate; // Filter for date-based count distribution
    private boolean allowWorkloadOverride; // Admin flag to allow assigning even if target user workload threshold is reached
}
