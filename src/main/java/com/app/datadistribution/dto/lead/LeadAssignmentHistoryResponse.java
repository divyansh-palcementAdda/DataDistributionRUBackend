package com.app.datadistribution.dto.lead;

import com.app.datadistribution.dto.user.UserResponse;
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
public class LeadAssignmentHistoryResponse {
    private UUID id;
    private UserResponse oldAssignedUser;
    private UserResponse newAssignedUser;
    private UserResponse changedBy;
    private LocalDateTime changedAt;
    private String remarks;
}
