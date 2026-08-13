package com.app.datadistribution.dto.lead;

import com.app.datadistribution.enums.SentimentCategory;
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
public class LeadStatusResponse {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private boolean active;
    private String status;
    private Integer displayOrder;
    private SentimentCategory sentimentCategory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
