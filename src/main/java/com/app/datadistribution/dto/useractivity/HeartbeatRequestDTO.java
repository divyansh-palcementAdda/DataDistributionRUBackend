package com.app.datadistribution.dto.useractivity;

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
public class HeartbeatRequestDTO {
    private Long clientTimestamp;
}
