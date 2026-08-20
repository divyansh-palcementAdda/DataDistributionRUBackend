package com.app.datadistribution.dto.lead;

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
public class UserDistributionSummaryDTO {

    private UUID userId;
    private String userName;
    private String userEmail;
    private long todayFollowUpCount;
    private long currentUnavailedLeadCount;
    private int remainingCapacity;
    private int assignedCount;
    private String status; // e.g. "SUCCESS", "SKIPPED"
    private String reason; // e.g. "DAILY_FOLLOWUP_LIMIT_REACHED", "MAX_CAPACITY_REACHED", "INACTIVE_USER", "NO_REMAINING_CAPACITY"
}
