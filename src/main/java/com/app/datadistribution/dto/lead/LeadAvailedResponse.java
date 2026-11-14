package com.app.datadistribution.dto.lead;

import java.time.LocalDateTime;
import java.util.UUID;

import com.app.datadistribution.dto.user.UserSummaryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

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
public class LeadAvailedResponse {
    private UUID id;
    private UUID leadId;
    private String leadCode;

    @JsonProperty("isAvailed")
    private boolean isAvailed;

    @JsonProperty("isAvailed")
    public boolean isAvailed() {
        return isAvailed;
    }

    @JsonProperty("isAvailed")
    public void setIsAvailed(boolean isAvailed) {
        this.isAvailed = isAvailed;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public void setAvailed(boolean availed) {
        this.isAvailed = availed;
    }

    private UserSummaryResponse availedBy;
    private LocalDateTime availedAt;


}
