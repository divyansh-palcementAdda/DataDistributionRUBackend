package com.app.datadistribution.dto.lead;

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
public class LeadSourceResponse {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private boolean active;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private Long totalData = 0L;

    @Builder.Default
    private Long totalAllottedData = 0L;

    @Builder.Default
    private Long totalAvailedData = 0L;
}
