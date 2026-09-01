package com.app.datadistribution.dto.lead;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class RescheduleFollowUpRequest {

    @NotNull(message = "New follow-up date and time is required")
    private LocalDateTime newFollowUpDate;

    @NotBlank(message = "Remarks/reason is required when rescheduling a follow-up.")
    private String remarks;
}
