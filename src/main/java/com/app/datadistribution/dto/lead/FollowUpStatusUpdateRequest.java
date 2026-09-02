package com.app.datadistribution.dto.lead;

import com.app.datadistribution.enums.FollowUpStatus;
import jakarta.validation.constraints.NotNull;
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
public class FollowUpStatusUpdateRequest {

    @NotNull(message = "Follow-up status is required.")
    private FollowUpStatus status;

    private String remarks;
    private String feedback;

    public String getEffectiveRemarks() {
        if (remarks != null && !remarks.isBlank()) {
            return remarks.trim();
        }
        if (feedback != null && !feedback.isBlank()) {
            return feedback.trim();
        }
        return "";
    }
}
