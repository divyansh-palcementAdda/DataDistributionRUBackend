package com.app.datadistribution.dto.lead;

import java.util.ArrayList;
import java.util.List;
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
public class LeadDistributionResponse {

    private long totalSelectedLeads;
    private long totalDistributableLeads;
    private long totalMatchingLeads;
    private long totalAvailableLeads;
    private int totalAssigned;
    private int totalUnassigned;
    private Integer requestedMaximumNumber;
    private Integer requestedMaximumPerUser;
    private boolean isPreviewOnly;

    @Builder.Default
    private List<UserDistributionSummaryDTO> users = new ArrayList<>();

    @Builder.Default
    private List<UnassignedLeadDTO> unassignedLeads = new ArrayList<>();
}

