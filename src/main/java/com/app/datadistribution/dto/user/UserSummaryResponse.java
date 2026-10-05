package com.app.datadistribution.dto.user;

import com.app.datadistribution.enums.HodAccessType;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight user summary used in nested/contextual references (createdBy, assignedTo,
 * changedBy, etc.) and department user listings.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String phone;
    private Boolean active;
    private String profileImage;
    private Set<String> roles;
    private String role;
    private HodAccessType hodAccessType;
}
