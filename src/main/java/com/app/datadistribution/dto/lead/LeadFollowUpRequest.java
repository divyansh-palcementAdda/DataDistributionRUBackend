package com.app.datadistribution.dto.lead;

import com.app.datadistribution.enums.FollowUpStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
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
public class LeadFollowUpRequest {

    @NotNull(message = "Follow-up date is required")
    private LocalDateTime followUpDate;

    private String remarks;

    @Builder.Default
    private FollowUpStatus status = FollowUpStatus.PENDING;
}
