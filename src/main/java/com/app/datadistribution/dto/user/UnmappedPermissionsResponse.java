package com.app.datadistribution.dto.user;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnmappedPermissionsResponse {
    private int count;
    private List<PermissionDTO> permissions;
}
