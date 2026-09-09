package com.app.datadistribution.dto.program;

import java.util.List;
import java.util.UUID;
import com.app.datadistribution.enums.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class ProgramRequestDTO {

    @NotBlank(message = "Program name is required")
    @Size(max = 150, message = "Program name must not exceed 150 characters")
    private String name;

    @NotBlank(message = "Program code is required")
    @Size(max = 50, message = "Program code must not exceed 50 characters")
    private String code;

    private String description;

    @Builder.Default
    private Status status = Status.ACTIVE;

    private List<UUID> courseIds;
}
