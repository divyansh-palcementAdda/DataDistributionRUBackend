package com.app.datadistribution.dto.analytics;

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
public class LeadStatusColumnDTO {
    private UUID statusId;
    private String code;
    private String name;
    private String sentimentCategory;
    private Integer displayOrder;
}
