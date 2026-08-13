package com.app.datadistribution.dto.dashboard;

import jakarta.validation.constraints.NotNull;
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
public class CardPreferenceUpdateRequest {
    @NotNull(message = "Visible parameter is required")
    private Boolean visible;
}
