package com.app.datadistribution.dto.coursetemplate;

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
public class CourseTemplateSummaryDTO {

    private UUID id;
    private String name;
    private String subject;
    private String channel;
}
