package com.app.datadistribution.dto.lead;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
public class LeadActionEnforcementDTO {

    private UUID leadId;

    private boolean restricted;

    private String reason;

    @Builder.Default
    private List<String> allowedActions = new ArrayList<>();

    private String message;

    @JsonProperty("actionRequired")
    public boolean isActionRequired() {
        return restricted;
    }

    @JsonProperty("actionType")
    public String getActionType() {
        return restricted ? "MANDATORY_LEAD_UPDATE" : "NONE";
    }
}
