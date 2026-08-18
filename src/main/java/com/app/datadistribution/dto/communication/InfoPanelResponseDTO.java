package com.app.datadistribution.dto.communication;

import com.app.datadistribution.dto.course.CourseSummaryDTO;
import com.app.datadistribution.dto.courseimage.CourseImageDTO;
import com.app.datadistribution.dto.courseusp.CourseUSPDTO;
import com.app.datadistribution.dto.coursetemplate.CourseTemplateResponseDTO;
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
public class InfoPanelResponseDTO {
    private UUID leadId;
    private String leadCode;
    private String leadFullName;
    private CourseSummaryDTO course;
    private CourseTemplateResponseDTO template;
    private CourseImageDTO activeImage;
    private String renderedSubject;
    private String renderedContent;
    private List<CourseUSPDTO> usps;
    private List<CourseImageDTO> availableImages;
}
