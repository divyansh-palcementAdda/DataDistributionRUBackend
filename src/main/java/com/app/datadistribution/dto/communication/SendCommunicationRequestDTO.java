package com.app.datadistribution.dto.communication;

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
public class SendCommunicationRequestDTO {

    private UUID courseId;
    private UUID templateId;
    
    /**
     * Optional imageId override. User must have COURSE_TEMPLATE_IMAGE_SELECT permission
     * to provide a non-null imageId.
     */
    private UUID imageId;

    private String recipientOverride;
    private String customMessageOverride;
}
