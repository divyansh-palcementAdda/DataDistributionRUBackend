package com.app.datadistribution.dto.feedback;

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
public class FeedbackResponseDTO {
    private UUID id;
    private UUID leadId;
    private String leadCode;
    private String leadFullName;
    private String feedback;
    private LeadStatus statusAtTime;
    private UserResponse createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
