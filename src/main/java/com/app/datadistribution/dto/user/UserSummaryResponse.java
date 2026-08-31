package com.app.datadistribution.dto.user;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight user summary used in nested/contextual references (createdBy, assignedTo,
 * changedBy, etc.). Does NOT expose sensitive fields like roles, permissions, or auth metadata.
 * For full user details use {@link UserResponse} via the /api/users endpoints.
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
    private String profileImage;
}
