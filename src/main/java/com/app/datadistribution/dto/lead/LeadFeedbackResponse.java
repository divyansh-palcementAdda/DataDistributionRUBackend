package com.app.datadistribution.dto.lead;

import java.time.LocalDateTime;
import java.util.UUID;

import com.app.datadistribution.dto.user.UserSummaryResponse;

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
public class LeadFeedbackResponse {
    private UUID id;
    private String feedback;
    private LeadStatusResponse statusAtTime;
    private UserSummaryResponse createdBy;
    private LocalDateTime createdAt;
}
