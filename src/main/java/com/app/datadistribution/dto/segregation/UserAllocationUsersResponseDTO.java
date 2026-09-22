package com.app.datadistribution.dto.segregation;

import com.app.datadistribution.common.PageResponseDTO;
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
public class UserAllocationUsersResponseDTO {
    private long totalUsers;
    private long currentlyWorkingUsers;
    private long totalAllottedData;
    private PageResponseDTO<UserAllocationRowDTO> users;
}
