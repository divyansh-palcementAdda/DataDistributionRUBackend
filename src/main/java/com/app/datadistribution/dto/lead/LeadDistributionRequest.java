package com.app.datadistribution.dto.lead;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
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
public class LeadDistributionRequest {

    @Valid
    private LeadDistributionFilterRequest filters;

    @NotEmpty(message = "At least one user ID must be selected for lead distribution.")
    private List<UUID> userIds;

    @Min(value = 1, message = "Maximum data per user must be greater than zero.")
    private int maximumDataPerUser;
}
