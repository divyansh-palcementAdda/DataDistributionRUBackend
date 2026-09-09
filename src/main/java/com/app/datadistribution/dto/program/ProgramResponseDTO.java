package com.app.datadistribution.dto.program;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.app.datadistribution.dto.course.CourseSummaryDTO;
import com.app.datadistribution.enums.Status;
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
public class ProgramResponseDTO {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private Status status;
    private List<CourseSummaryDTO> courses;
    private int totalCourses;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
