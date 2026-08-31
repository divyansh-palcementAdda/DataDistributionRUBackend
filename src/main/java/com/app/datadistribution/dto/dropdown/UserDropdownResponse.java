package com.app.datadistribution.dto.dropdown;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight user dropdown response DTO. Contains zero sensitive auth/role metadata.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDropdownResponse {
    private UUID id;
    private String name;
    private String username;
    private String email;
}
