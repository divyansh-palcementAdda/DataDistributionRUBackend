package com.app.datadistribution.dto.dashboard;

import java.time.LocalDateTime;
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
public class UserNotLoggedInSummaryDTO {

    private UUID userId;
    private String name;
    private String username;
    private String email;
    private List<String> roleNames;
    private List<String> departmentNames;
    private LocalDateTime lastSuccessfulLoginAt;
    private boolean hasLoggedInToday;
}
