package com.app.datadistribution.dto.lead;

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
public class LeadMatrixResponseDTO<T> {
    private List<LeadMatrixStatusDTO> statuses;
    private List<T> rows;
}
