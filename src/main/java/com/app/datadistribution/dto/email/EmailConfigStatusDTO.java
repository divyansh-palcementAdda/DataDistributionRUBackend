package com.app.datadistribution.dto.email;

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
public class EmailConfigStatusDTO {

    private boolean enabled;
    private String host;
    private int port;
    private String fromEmail;
    private String fromName;
    private boolean async;
    private int maxRetries;
    private String dailyReminderCron;
    private String dailyReminderZone;
    private String frontendUrl;
}
