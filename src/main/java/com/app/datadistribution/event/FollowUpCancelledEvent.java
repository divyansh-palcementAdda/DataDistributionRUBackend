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
public class FollowUpCancelledEvent {

    private UUID followUpId;
    private UUID leadId;
    private UUID assignedUserId;
    private LocalDateTime scheduledDate;
    private LocalDateTime cancelledAt;
    private String remarks;
    private String cancellationRemarks;
    private UUID cancelledByUserId;
}
