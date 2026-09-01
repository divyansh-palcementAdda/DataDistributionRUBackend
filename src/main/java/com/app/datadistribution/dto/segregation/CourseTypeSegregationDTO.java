package com.app.datadistribution.dto.segregation;

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
public class CourseTypeSegregationDTO {
    private UUID id;
    private String name;
    private String description;
    private long totalLeads;
}
