package com.app.datadistribution.dto.dropdown;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight course dropdown response DTO.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDropdownResponse {
    private UUID id;
    private String name;
    private String code;
    private UUID courseTypeId;
    private String courseTypeName;
}
