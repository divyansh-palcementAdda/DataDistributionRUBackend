package com.app.datadistribution.dto.report;

import java.time.LocalDate;
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
public class AcademicSessionDTO {
    private String sessionId;
    private String sessionName;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean isCurrent;
}
