package com.app.datadistribution.dto.followup;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.app.datadistribution.enums.FollowUpStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpFilterCriteria {
    private LocalDate date;
    private LocalDate dueDate;
    private LocalDate fromDate;
    private LocalDate toDate;
    private FollowUpStatus status;
    private FollowUpStatus followUpStatus;
    private String outcome;
    private String followUpOutcome;
    private String category;
    private UUID userId;
    private UUID assignedUserId;
    private UUID departmentId;
    private UUID leadId;
    private UUID leadStatusId;
    private List<UUID> leadStatusIds;

    public LocalDate getEffectiveDate() {
        if (date != null) return date;
        if (dueDate != null) return dueDate;
        return null;
    }

    public FollowUpStatus getEffectiveStatus() {
        if (status != null) return status;
        if (followUpStatus != null) return followUpStatus;
        return null;
    }

    public String getEffectiveOutcome() {
        if (outcome != null && !outcome.isBlank()) return outcome.trim().toUpperCase();
        if (followUpOutcome != null && !followUpOutcome.isBlank()) return followUpOutcome.trim().toUpperCase();
        return null;
    }

    public UUID getEffectiveUserId() {
        if (userId != null) return userId;
        if (assignedUserId != null) return assignedUserId;
        return null;
    }
}
