package com.app.datadistribution.dto.followup;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Minimal DTO for dynamic Follow-Up status dashboard cards.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpStatusCountDTO {

    private UUID statusId;
    private String statusName;
    private String statusCode;
    private long count;
}
