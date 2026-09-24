package com.app.datadistribution.dto.infopanel;

import java.time.LocalDateTime;
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
public class CompetitorResponseDTO {
    private UUID id;
    private String collegeName;
    private Integer displayOrder;
    private boolean active;
    private List<String> branches;
    private CompetitorComparisonDTO comparison;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
