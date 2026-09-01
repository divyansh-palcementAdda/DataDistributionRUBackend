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
public class LeadReassignedEvent {

    private UUID targetUserId;
    private UUID sourceUserId;
    private String sourceUserName;
    private UUID reassignedByUserId;
    private int reassignedCount;
    private String departmentName;
    private String reason;
    private LocalDateTime reassignmentTime;
    private String batchId;
}
