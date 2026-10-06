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
public class StreamNodeDTO {
    private UUID streamId;
    private String streamName;
    private String streamCode;
    private long total;
    private long allotted;
    private long unallotted;
    private long availed;

    @Builder.Default
    private List<GradeNodeDTO> grades = new ArrayList<>();
}
