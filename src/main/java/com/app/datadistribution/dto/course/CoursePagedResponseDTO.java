package com.app.datadistribution.dto.course;

import java.util.List;
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
public class CoursePagedResponseDTO {
    private List<CourseResponseDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;
}
