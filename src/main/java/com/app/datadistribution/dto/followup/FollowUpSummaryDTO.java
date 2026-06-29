package com.app.datadistribution.dto.followup;

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
public class FollowUpSummaryDTO {
    private long todayFollowUps;
    private long pendingFollowUps;
    private long completedFollowUps;
    private long overdueFollowUps;
}
