package com.app.datadistribution.dto.followup;

import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.enums.FollowUpStatus;
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
public class FollowUpResponseDTO {
    private UUID id;
    private UUID leadId;
    private String leadCode;
    private String leadFullName;
    private LocalDateTime followUpDate;
    private String remarks;
    private FollowUpStatus status;
    private boolean completed;
    private LocalDateTime completedAt;
    private UserResponse createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
