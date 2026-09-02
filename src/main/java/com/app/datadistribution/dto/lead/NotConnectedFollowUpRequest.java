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
public class NotConnectedFollowUpRequest {

    @NotBlank(message = "Remarks/feedback is required when marking a follow-up as not connected.")
    private String remarks;

    public String getFeedback() {
        return remarks;
    }

    public void setFeedback(String feedback) {
        this.remarks = feedback;
    }
}
