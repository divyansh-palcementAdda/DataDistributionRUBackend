package com.app.datadistribution.dto.lead;

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

    private long totalMatchingLeads;
    private long totalAvailableLeads;
    private int totalAssigned;
    private int requestedMaximumPerUser;
    private boolean isPreviewOnly;
    private List<UserDistributionSummaryDTO> users;
}
