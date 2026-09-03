package com.app.datadistribution.dto.lead;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ManualRegistrationApprovalRequest {

    @NotNull(message = "Registered course ID is required for registration approval")
    private UUID registeredCourseId;

    private String enrollmentId;
    private String remarks;
}
