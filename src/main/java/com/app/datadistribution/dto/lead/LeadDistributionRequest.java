package com.app.datadistribution.dto.lead;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
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

    /**
     * Exact Lead IDs explicitly selected by Admin for distribution.
     * When present, this exact list is the source of truth for distribution.
     */
    private List<UUID> leadIds;

    /**
     * Optional filter criteria (used when distributing all matching leads from a page/filter without explicit manual selection).
     */
    @Valid
    private LeadDistributionFilterRequest filters;

    /**
     * Target user IDs to receive distributed leads.
     */
    @NotEmpty(message = "At least one user ID must be selected for lead distribution.")
    private List<UUID> userIds;

    /**
     * Maximum total selected leads to distribute across all users in this operation.
     */
    private Integer maximumNumber;

    /**
     * Backward-compatible alias for maximumNumber / maximum capacity constraint.
     */
    private Integer maximumDataPerUser;

    public int resolveEffectiveMaxLeads() {
        if (maximumNumber != null && maximumNumber > 0) {
            return maximumNumber;
        }
        if (maximumDataPerUser != null && maximumDataPerUser > 0) {
            return maximumDataPerUser;
        }
        return Integer.MAX_VALUE;
    }
}
