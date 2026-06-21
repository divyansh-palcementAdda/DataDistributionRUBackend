package com.app.datadistribution.dto.user;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
public class RoleDTO {

    private UUID id;
    private String name;
    private String description;
    private boolean active;
    private Long userCount;

    public RoleDTO(UUID id, String name, Long userCount) {
        this.id = id;
        this.name = name;
        this.userCount = userCount;
    }

    public RoleDTO(UUID id, String name, String description, boolean active, Long userCount) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.active = active;
        this.userCount = userCount;
    }
}
