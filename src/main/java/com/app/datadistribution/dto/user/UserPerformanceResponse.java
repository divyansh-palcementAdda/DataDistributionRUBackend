package com.app.datadistribution.dto.user;

import java.util.List;
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
public class UserPerformanceResponse {

    private UUID userId;
    private String userName;
    private String email;
    private String phone;
    private List<String> roles;
    private String department;
    private boolean active;
    private String status;

    // Lead metrics
    @Builder.Default
    private Long totalAllottedData = 0L;

    @Builder.Default
    private Long totalAvailedData = 0L;

    @Builder.Default
    private Long rawDataCount = 0L;

    @Builder.Default
    private Long registeredDataCount = 0L;

    // Follow-up metrics
    @Builder.Default
    private Long todayFollowupsCount = 0L;

    @Builder.Default
    private Long todayFollowupsScheduled = 0L;

    @Builder.Default
    private Long todayMissedFollowups = 0L;

    @Builder.Default
    private Long todayUpcomingFollowups = 0L;

    @Builder.Default
    private Long todayPendingFollowups = 0L;

    // Call metrics
    @Builder.Default
    private Long todayConnectedCalls = 0L;

    // Activity & Session metrics
    @Builder.Default
    private Boolean currentlyWorking = false;

    @Builder.Default
    private Long todayLoginCount = 0L;

    @Builder.Default
    private Long todayLogoutCount = 0L;

    @Builder.Default
    private Double todayWorkingHours = 0.0;
}
