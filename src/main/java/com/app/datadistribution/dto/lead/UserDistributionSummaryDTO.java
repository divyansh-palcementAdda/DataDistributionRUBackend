package com.app.datadistribution.dto.lead;

import java.util.List;
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
public class UserDistributionSummaryDTO {

    private UUID userId;
    private String userName;
    private String userEmail;
    
    private long todayFollowUpCount;
    private int followupCapacity;
    
    private long currentRawCount;
    private int rawCapacity;
    
    private int finalCapacity;
    private int assignedCount;
    private List<UUID> assignedLeadIds;
    
    private String status; // "SUCCESS", "SKIPPED", "PARTIAL"
    private String reason; // Detailed reason description
}
