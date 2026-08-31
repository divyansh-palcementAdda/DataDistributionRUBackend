package com.app.datadistribution.dto.lead;

import jakarta.validation.constraints.NotBlank;
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
public class CancelFollowUpRequest {

    @NotBlank(message = "Remarks/feedback is required when cancelling a follow-up.")
    private String feedback;

    public String getRemarks() {
        return feedback;
    }

    public void setRemarks(String remarks) {
        this.feedback = remarks;
    }
}
