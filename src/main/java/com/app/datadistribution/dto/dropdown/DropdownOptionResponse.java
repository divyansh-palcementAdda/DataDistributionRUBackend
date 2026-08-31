package com.app.datadistribution.dto.dropdown;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ultra-lightweight generic dropdown option DTO.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DropdownOptionResponse {
    private UUID id;
    private String name;
    private String code;
}
