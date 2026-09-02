package com.app.datadistribution.dto.user;

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
public class UserPerformanceFilterRequest {

    private String search;
    private String role;
    private List<String> roles;
    private UUID departmentId;
    private String status;
    private Boolean currentlyWorking;
    private Integer page;
    private Integer size;
    private String sortBy;
    private String sortDirection;
}
