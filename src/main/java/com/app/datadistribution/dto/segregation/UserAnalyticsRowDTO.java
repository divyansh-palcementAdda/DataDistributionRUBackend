package com.app.datadistribution.dto.segregation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class UserAnalyticsRowDTO {
    private UUID userId;
    private String fullName;
    private String username;
    private String email;
    private String department;
    private List<String> roles;
    private long total;
    private long allotted;
    private long unallotted;
    private long availed;

    @Builder.Default
    private Map<String, Long> statusCounts = new HashMap<>();
}
