package com.app.datadistribution.dto.dropdown;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight lead status dropdown response DTO.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadStatusDropdownResponse {
    private UUID id;
    private String name;
    private String code;
    private String sentimentCategory;
    private Integer displayOrder;
    private boolean isFollowUpStatus;
}
