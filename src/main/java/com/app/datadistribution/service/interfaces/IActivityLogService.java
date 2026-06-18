package com.app.datadistribution.service.interfaces;

import java.util.List;

import com.app.datadistribution.dto.ActivityLogDTO;
import com.app.datadistribution.enums.ActivityType;
import com.app.datadistribution.exception.BadRequestException;

public interface IActivityLogService {

    void logActivity(ActivityType type, String description, String performedBy);

    void logActivity(ActivityType type, String description);

    List<ActivityLogDTO> getRecentActivities(int limit);

    List<ActivityLogDTO> getRecentActivitiesByType(ActivityType type, int limit) throws BadRequestException;

    List<ActivityLogDTO> getActivitiesByUser(String usernameOrEmail, int limit) throws BadRequestException;

    long cleanupOldActivities(int olderThanDays) throws BadRequestException;
    
    void logActivity(ActivityType type, String title, String description, String performedBy);
}