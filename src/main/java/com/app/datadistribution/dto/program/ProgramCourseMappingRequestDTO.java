package com.app.datadistribution.dto.program;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
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
public class ProgramCourseMappingRequestDTO {

    @NotEmpty(message = "At least one course ID must be provided")
    private List<UUID> courseIds;
}
