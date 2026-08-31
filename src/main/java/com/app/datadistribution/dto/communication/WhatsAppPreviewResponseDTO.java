package com.app.datadistribution.dto.communication;

import com.app.datadistribution.dto.course.CourseSummaryDTO;
import com.app.datadistribution.dto.courseusp.CourseUSPDTO;
import com.app.datadistribution.dto.coursetemplate.CourseTemplateSummaryDTO;
import com.app.datadistribution.dto.lead.LeadSummaryDTO;
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
public class WhatsAppPreviewResponseDTO {

    private LeadSummaryDTO lead;
    private CourseSummaryDTO course;
    private CourseUSPDTO usp;
    private CourseTemplateSummaryDTO template;
    private String message;
    private String imageUrl;
    private String whatsAppUrl;
}
