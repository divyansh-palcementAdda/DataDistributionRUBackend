package com.app.datadistribution.dto.lead;

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
public class LeadSourceStatsResponse {
    private UUID sourceId;
    private String sourceName;
    private long count;
    private double percentage;
}
