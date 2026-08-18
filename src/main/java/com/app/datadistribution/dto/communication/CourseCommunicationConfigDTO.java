package com.app.datadistribution.dto.communication;

import com.app.datadistribution.dto.courseimage.CourseImageDTO;
import com.app.datadistribution.dto.coursetemplate.CourseTemplateResponseDTO;
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
public class CourseCommunicationConfigDTO {
    private UUID id;
    private UUID courseId;

    private CourseTemplateResponseDTO infoPanelTemplate;
    private CourseTemplateResponseDTO emailTemplate;
    private CourseTemplateResponseDTO whatsappTemplate;

    private CourseImageDTO infoPanelImage;
    private CourseImageDTO emailImage;
    private CourseImageDTO whatsappImage;

    // Request payload fields for updating configuration
    private UUID infoPanelTemplateId;
    private UUID emailTemplateId;
    private UUID whatsappTemplateId;

    private UUID infoPanelImageId;
    private UUID emailImageId;
    private UUID whatsappImageId;
}
