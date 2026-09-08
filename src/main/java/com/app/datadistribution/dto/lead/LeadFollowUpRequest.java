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

    private java.util.UUID leadId;

    private java.util.UUID leadStatusId;

    @NotNull(message = "Follow-up date is required")
    private LocalDateTime followUpDate;

    @jakarta.validation.constraints.NotBlank(message = "Remarks/feedback is required while scheduling a follow-up")
    private String remarks;

    /**
     * @deprecated Initial status is automatically determined by the backend based on the scheduled date.
     * Any client-supplied value is ignored on creation.
     */
    private FollowUpStatus status;
}
