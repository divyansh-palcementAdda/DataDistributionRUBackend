package com.app.datadistribution.dto.communication;

import jakarta.validation.constraints.NotNull;
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
public class WhatsAppPreviewRequestDTO {

    @NotNull(message = "Course ID is required")
    private UUID courseId;

    private UUID uspId;

    private UUID templateId;

    private UUID imageId;
}
