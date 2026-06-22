package com.app.datadistribution.dto.course;

import com.app.datadistribution.enums.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CourseTypeRequestDTO {

    @NotBlank(message = "Course type name is required")
    @Size(max = 100, message = "Course type name must be less than 100 characters")
    private String name;

    @Size(max = 255, message = "Description must be less than 255 characters")
    private String description;

    @NotNull(message = "Status is required")
    @Builder.Default
    private Status status = Status.ACTIVE;
}
