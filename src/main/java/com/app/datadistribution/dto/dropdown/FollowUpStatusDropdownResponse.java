package com.app.datadistribution.dto.dropdown;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Minimal DTO for Follow-Up lifecycle state dropdown.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpStatusDropdownResponse {

    private String name;
    private String displayName;
}
