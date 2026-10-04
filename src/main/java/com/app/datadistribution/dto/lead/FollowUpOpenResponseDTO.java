package com.app.datadistribution.dto.lead;

import java.time.LocalDateTime;
import java.util.UUID;

import com.app.datadistribution.enums.FollowUpStatus;

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
public class FollowUpOpenResponseDTO {

    private UUID followUpId;

    private UUID leadId;

    private FollowUpStatus status;

    private boolean completed;

    private LocalDateTime completedAt;

    private boolean leadActionRequired;

    private LeadActionEnforcementDTO actionEnforcement;
}
