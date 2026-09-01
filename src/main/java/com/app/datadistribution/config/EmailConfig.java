package com.app.datadistribution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "app.mail")
@Getter
@Setter
public class EmailConfig {

    private String fromEmail = "chancelloroffice@renaissance.ac.in";
    private String fromName = "Renaissance University CRM";
    private boolean enabled = true;
    private boolean async = true;
    private int maxRetries = 3;
    private String dailyReminderCron = "0 30 9 * * *";
    private String dailyReminderZone = "Asia/Kolkata";
}
