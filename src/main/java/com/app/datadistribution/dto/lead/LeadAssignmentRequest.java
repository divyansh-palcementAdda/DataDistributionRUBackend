package com.app.datadistribution.dto.lead;

import jakarta.validation.constraints.NotNull;
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
public class LeadAssignmentRequest {

    @NotNull(message = "Assigned user ID is required")
    private UUID assignedToUserId;

    private String remarks;
}
