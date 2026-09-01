package com.app.datadistribution.dto.segregation;

import java.util.ArrayList;
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
public class SegregationMatrixResponseDTO {
    private UUID courseTypeId;
    private String courseTypeName;
    private long totalLeads;
    private long allottedLeads;
    private long unallottedLeads;
    private long availedLeads;

    @Builder.Default
    private List<SourceNodeDTO> sources = new ArrayList<>();
}
