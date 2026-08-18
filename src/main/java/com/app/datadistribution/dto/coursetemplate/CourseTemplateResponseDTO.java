package com.app.datadistribution.dto.coursetemplate;

import com.app.datadistribution.dto.course.CourseSummaryDTO;
import java.time.LocalDateTime;
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
public class CourseTemplateResponseDTO {

    private UUID id;
    private String name;
    private String subject;
    private String content;
    private String channel;
    private CourseSummaryDTO course;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
