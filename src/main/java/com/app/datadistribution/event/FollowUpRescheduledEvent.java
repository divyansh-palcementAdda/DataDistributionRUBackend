package com.app.datadistribution.event;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FollowUpRescheduledEvent {

    private UUID followUpId;
    private UUID leadId;
    private UUID assignedUserId;
    private LocalDateTime previousFollowUpDate;
    private LocalDateTime newFollowUpDate;
    private String remarks;
    private String followUpStatus;
    private UUID rescheduledByUserId;
}
