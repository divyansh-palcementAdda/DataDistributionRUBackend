package com.app.datadistribution.payload;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
public class RoleDTO {

    private Long id;
    private String name;

    // Derived / summary field
    private Long userCount;

    // JPQL Projection constructor
    public RoleDTO(Long id, String name, Long userCount) {
        this.id = id;
        this.name = name;
        this.userCount = userCount;
    }
}
