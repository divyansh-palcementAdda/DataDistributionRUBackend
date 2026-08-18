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
public class DashboardAnalyticsResponseDTO {
    private String groupBy;
    private List<GroupCountDTO> data;
    private long total;
    private Integer page;
    private Integer pageSize;
    private Integer totalPages;
}
