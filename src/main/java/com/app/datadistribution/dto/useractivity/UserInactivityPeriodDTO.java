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
public class UserInactivityPeriodDTO {
    private UUID periodId;
    private UUID sessionId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private long durationMinutes;
    private long durationSeconds;
    private String formattedDuration;
    private String reason;
}
