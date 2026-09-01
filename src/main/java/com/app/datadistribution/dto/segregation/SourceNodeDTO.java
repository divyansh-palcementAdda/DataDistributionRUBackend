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
public class SourceNodeDTO {
    private UUID sourceId;
    private String sourceName;
    private String sourceCode;
    private long total;
    private long allotted;
    private long unallotted;
    private long availed;

    @Builder.Default
    private List<BoardNodeDTO> boards = new ArrayList<>();
}
