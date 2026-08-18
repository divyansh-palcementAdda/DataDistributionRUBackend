package com.app.datadistribution.dto.coursetemplate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CourseTemplateRequestDTO {

    @NotBlank(message = "Template name is required")
    @Size(max = 150, message = "Template name must be less than 150 characters")
    private String name;

    @Size(max = 255, message = "Subject must be less than 255 characters")
    private String subject;

    @NotBlank(message = "Template content is required")
    private String content;

    @NotBlank(message = "Channel is required (EMAIL, SMS, WHATSAPP)")
    private String channel;

    @NotNull(message = "Course ID is required")
    private UUID courseId;

    @Builder.Default
    private boolean active = true;
}
