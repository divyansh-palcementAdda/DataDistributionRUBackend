package com.app.datadistribution.dto.dashboard;

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
public class DashboardFollowUpCountResponseDTO {
    private long count;
    private String type;
    private LocalDate date;
}
