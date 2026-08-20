package com.app.datadistribution.dto.reassign;

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
public class LeadReassignResponse {
    private String message;
    private UUID sourceUserId;
    private String sourceUserName;
    private int totalRequested;
    private int totalReassigned;
    private int totalPendingFollowUpsReassigned;
    private List<UserLeadDistributionSummary> assignments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserLeadDistributionSummary {
        private UUID targetUserId;
        private String targetUserName;
        private int count;
        private int pendingFollowUpsCount;
    }
}
