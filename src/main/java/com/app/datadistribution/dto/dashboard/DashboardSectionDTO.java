package com.app.datadistribution.dto.dashboard;

import java.util.List;
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
public class DashboardSectionDTO {
    private String code;
    private String name;
    private List<DashboardCardDTO> cards;
}
