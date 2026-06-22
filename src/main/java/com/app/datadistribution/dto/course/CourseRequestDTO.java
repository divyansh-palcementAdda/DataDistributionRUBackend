package com.app.datadistribution.dto.course;

import com.app.datadistribution.enums.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
public class CourseRequestDTO {

    @NotBlank(message = "Course name is required")
    @Size(max = 150, message = "Course name must be less than 150 characters")
    private String courseName;

    @NotBlank(message = "Course code is required")
    @Size(max = 50, message = "Course code must be less than 50 characters")
    private String courseCode;

    private String description;

    @NotNull(message = "Duration is required")
    @Positive(message = "Duration must be a positive integer")
    private Integer duration;

    @NotBlank(message = "Duration unit is required")
    @Size(max = 50, message = "Duration unit must be less than 50 characters")
    private String durationUnit;

    @NotNull(message = "Fees are required")
    @PositiveOrZero(message = "Fees must be zero or positive")
    private Double fees;

    @NotNull(message = "Course type is required")
    private UUID courseTypeId;

    @NotNull(message = "Status is required")
    @Builder.Default
    private Status status = Status.ACTIVE;
}
