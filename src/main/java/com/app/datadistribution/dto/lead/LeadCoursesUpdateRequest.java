package com.app.datadistribution.dto.lead;

import jakarta.validation.constraints.NotEmpty;
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
public class LeadCoursesUpdateRequest {

    @NotEmpty(message = "Course IDs list cannot be empty")
    private List<UUID> courseIds;
}
