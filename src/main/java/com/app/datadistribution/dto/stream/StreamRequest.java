package com.app.datadistribution.dto.stream;

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
public class StreamRequest {

    @NotBlank(message = "Stream name is required")
    @Size(max = 100, message = "Stream name must be less than 100 characters")
    private String name;

    @NotBlank(message = "Stream code is required")
    @Size(max = 50, message = "Stream code must be less than 50 characters")
    private String code;

    @Size(max = 255, message = "Description must be less than 255 characters")
    private String description;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private Integer displayOrder = 0;
}
