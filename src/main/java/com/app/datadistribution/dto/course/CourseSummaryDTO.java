package com.app.datadistribution.dto.course;

import com.app.datadistribution.enums.Status;
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
public class CourseSummaryDTO {
    private UUID id;
    private String courseName;
    private String courseCode;
    private Status status;
}
