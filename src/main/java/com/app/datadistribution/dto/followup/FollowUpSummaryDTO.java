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
    private long allFollowUps;
    private long todayFollowUps;
    private long todayContinuousFollowUps;
    private long todayNotConnectedFollowUps;
    private long pendingFollowUps;
    private long upcomingFollowUps;
    private long completedFollowUps;
    private long missedFollowUps;
    private long cancelledFollowUps;
    private long notConnectedFollowUps;
    private long overdueFollowUps;
}
