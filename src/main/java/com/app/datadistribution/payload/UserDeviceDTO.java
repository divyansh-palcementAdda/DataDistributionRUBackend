package com.app.datadistribution.payload;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeviceDTO {
    private Long id;
    private String deviceName;
    private String browser;
    private String os;
    private String ipAddress;
    private Instant createdAt;       // Login time
    private Instant lastActivityAt;   // Last activity
    private Instant logoutAt;         // Logout time
    private boolean isActive;
    private boolean isCurrentDevice;
}
