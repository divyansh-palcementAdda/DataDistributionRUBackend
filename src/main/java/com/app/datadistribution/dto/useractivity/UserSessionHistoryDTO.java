package com.app.datadistribution.dto.useractivity;

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
public class UserSessionHistoryDTO {
    private UUID sessionId;
    private LocalDateTime loginAt;
    private LocalDateTime logoutAt;
    private LocalDateTime lastActivityAt;
    private long workingDurationMinutes;
    private long workingDurationSeconds;
    private long inactiveDurationMinutes;
    private long inactiveDurationSeconds;
    private String formattedWorkingDuration;
    private String formattedInactiveDuration;
    private String logoutReason;
    private String sessionStatus;
    private String ipAddress;
    private String deviceInfo;
}
