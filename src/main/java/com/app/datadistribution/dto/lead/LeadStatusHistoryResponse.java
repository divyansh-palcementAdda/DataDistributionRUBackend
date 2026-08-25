package com.app.datadistribution.dto.lead;

import java.time.LocalDateTime;
import java.util.UUID;

import com.app.datadistribution.dto.user.UserResponse;

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
public class LeadStatusHistoryResponse {
    private UUID id;
    private UUID leadId;
    private String leadCode;
    private LeadStatusResponse previousStatus;
    private LeadStatusResponse newStatus;
    private UserResponse changedBy;
    private LocalDateTime changedAt;
    private String feedback;

    public LeadStatusResponse getOldStatus() {
        return previousStatus;
    }

    public LeadStatusResponse getCurrentStatus() {
        return newStatus;
    }

    public String getRemarks() {
        return feedback;
    }
}
