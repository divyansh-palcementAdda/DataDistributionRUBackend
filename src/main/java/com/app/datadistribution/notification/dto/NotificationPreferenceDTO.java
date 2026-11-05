package com.app.datadistribution.notification.dto;

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
public class NotificationPreferenceDTO {

    private UUID userId;
    private boolean followUpRemindersEnabled;
    private boolean leadAllocationEnabled;
    private boolean leadReassignmentEnabled;
    private boolean taskAssignmentEnabled;
    private boolean emailNotificationsEnabled;
    private boolean browserNotificationsEnabled;
}

