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
public class LeadMatrixStatusDTO {
    private UUID statusId;
    private String name;
    private String code;
    private String sentimentCategory;
    private boolean followUpStatus;
    private int displayOrder;
}
