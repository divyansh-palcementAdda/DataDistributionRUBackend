package com.app.datadistribution.dto.segregation;

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
public class UserAllocationSummaryDTO {
    private long totalUsersWithAllottedData;
    private long usersCurrentlyWorking;
}
