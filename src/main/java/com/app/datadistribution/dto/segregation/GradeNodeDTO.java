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
public class GradeNodeDTO {
    private UUID gradeId;
    private String gradeName;
    private String gradeCode;
    private long total;
    private long allotted;
    private long unallotted;
    private long availed;
}
