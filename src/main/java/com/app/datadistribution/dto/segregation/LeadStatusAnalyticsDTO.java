package com.app.datadistribution.dto.segregation;

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
public class LeadStatusAnalyticsDTO {
    private UUID statusId;
    private String name;
    private String code;
    private String sentimentCategory;
    private Integer displayOrder;
    private long count;
}
