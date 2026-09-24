package com.app.datadistribution.dto.infopanel;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
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
public class CompetitorRequestDTO {
    private UUID id;

    @NotBlank(message = "College name is required")
    private String collegeName;

    @Builder.Default
    private Integer displayOrder = 0;

    @Builder.Default
    private Boolean active = true;

    private List<String> branches;

    private CompetitorComparisonDTO comparison;
}
