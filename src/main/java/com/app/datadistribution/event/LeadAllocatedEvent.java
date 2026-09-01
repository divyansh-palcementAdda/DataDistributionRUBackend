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
public class LeadAllocatedEvent {

    private UUID targetUserId;
    private UUID allocatedByUserId;
    private int allocatedCount;
    private String departmentName;
    private LocalDateTime allocationTime;
    private String batchId;
}
