package com.app.datadistribution.dto.segregation;

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
public class UserAllocationRowDTO {
    private UUID userId;
    private String name;
    private String username;
    private String email;
    private String department;
    private List<String> roles;
    private long totalAllottedData;
    private long currentlyWorkingData;
    private boolean currentlyWorking;
    private LocalDateTime lastActivityAt;
}
