package com.app.datadistribution.dto.useractivity;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class UserDailyActivityResponseDTO {
    private UUID userId;
    private String username;
    private String fullName;
    private String email;
    private String department;
    private LocalDate date;

    // Real-time Status
    private String currentStatus; // WORKING, INACTIVE, LOGGED_OUT
    private Long inactiveMinutesCurrent; // How long user has been currently inactive (if INACTIVE)
    private LocalDateTime lastActivityAt;

    // Session Metrics
    private LocalDateTime firstLoginAt;
    private LocalDateTime lastLogoutAt;
    private int loginCount;
    private int inactiveCount;
    private long totalWorkingMinutes;
    private long totalWorkingSeconds;
    private long totalInactiveMinutes;
    private long totalInactiveSeconds;

    // Formatted Strings for UI Convenience
    private String formattedWorkingHours; // e.g. "07h 12m"
    private String formattedInactiveDuration; // e.g. "01h 05m"

    // CRM Productivity Metrics
    private long totalAvailed;
    private long totalFollowUpsTaken;
    private long totalFollowUpsScheduled;
    private long totalFollowUpsMissed;
    private long totalConnected;
    private long totalNotConnected;
}
