package com.app.datadistribution.dto.program;

import java.util.UUID;
import com.app.datadistribution.enums.Status;
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
public class ProgramSummaryDTO {
    private UUID id;
    private String name;
    private String code;
    private Status status;
}
