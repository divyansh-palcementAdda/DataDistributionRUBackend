package com.app.datadistribution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "app.user-management")
@Getter
@Setter
public class UserManagementProperties {

    /**
     * Controls whether ADMIN and SUPER_ADMIN users can be created or assigned.
     * When false (default), ADMIN and SUPER_ADMIN creation/assignment is disabled.
     * When true, creation and assignment are enabled according to RBAC.
     */
    private boolean allowAdminCreation = false;
}
