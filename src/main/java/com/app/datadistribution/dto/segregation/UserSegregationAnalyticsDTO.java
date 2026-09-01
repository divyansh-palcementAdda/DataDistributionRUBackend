package com.app.datadistribution.dto.segregation;

import java.util.ArrayList;
import java.util.List;
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
public class UserSegregationAnalyticsDTO {
    private UUID courseTypeId;
    private UUID leadSourceId;
    private UUID boardId;
    private UUID gradeId;

    @Builder.Default
    private List<LeadStatusColumnDTO> statusColumns = new ArrayList<>();

    @Builder.Default
    private List<UserAnalyticsRowDTO> users = new ArrayList<>();
}
