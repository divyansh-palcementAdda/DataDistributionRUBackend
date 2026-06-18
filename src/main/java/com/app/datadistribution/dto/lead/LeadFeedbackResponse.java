package com.app.datadistribution.dto.lead;

import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.enums.LeadStatus;
import java.time.LocalDateTime;
import java.util.UUID;
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
    private LeadStatus statusAtTime;
    private UserResponse createdBy;
    private LocalDateTime createdAt;
}
