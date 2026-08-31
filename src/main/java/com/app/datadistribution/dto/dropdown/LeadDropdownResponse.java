package com.app.datadistribution.dto.dropdown;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight lead dropdown response DTO.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadDropdownResponse {
    private UUID id;
    private String leadCode;
    private String fullName;
    private String phoneNumber;
}
