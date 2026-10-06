package com.app.datadistribution.dto.stream;

import java.time.LocalDateTime;
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
public class StreamResponse {

    private UUID id;
    private String name;
    private String code;
    private String description;
    private boolean active;
    private String status;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Aggregated lead counts within user's data scope
    private long totalData;
    private long totalAllottedData;
    private long totalUnallottedData;
    private long totalAvailedData;
}
