package com.app.datadistribution.service.interfaces;

import java.util.List;

import com.app.datadistribution.Model.ActivityType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.payload.ActivityLogDTO;

/**
 * Service interface for managing system activity logs and recent activities feed.
 * <p>
 * This service is responsible for:
 * - Logging user/system actions (audit trail)
 * - Providing recent activities for dashboard/activity tabs
 * - Maintaining consistent activity formatting for frontend
 * </p>
 */
public interface IActivityLogService {

    /**
     * Logs a new system activity/event.
     * <p>
     * This method should be called from other services when important business events occur
     * (e.g., new admission, consultancy created, fee payment received, etc.).
     * </p>
     *
     * @param type          the type/category of activity (determines icon, color, default title)
     * @param description   detailed description of what happened (shown in activity feed)
     * @param performedBy   username, email, "System", or agent name who triggered the action
     *                      (can be null → will default to "System")
     */
    void logActivity(ActivityType type, String description, String performedBy);

    /**
     * Convenience overload — uses current authenticated user (or "System") automatically.
     *
     * @param type        activity type
     * @param description what happened
     */
    void logActivity(ActivityType type, String description);

    /**
     * Retrieves the most recent activities for display in dashboard / activity tab.
     * <p>
     * Results are ordered by creation time (newest first).
     * </p>
     *
     * @param limit maximum number of activities to return (recommended: 10–30)
     * @return list of formatted DTOs ready for frontend (with icon, bg color, human-readable time, etc.)
     */
    List<ActivityLogDTO> getRecentActivities(int limit);

    /**
     * Retrieves recent activities filtered by activity type.
     * Useful when frontend wants to show only admissions or only payments, etc.
     *
     * @param type  filter by specific activity type
     * @param limit maximum number of results
     * @return filtered and formatted activity list
     */
    List<ActivityLogDTO> getRecentActivitiesByType(ActivityType type, int limit)  throws BadRequestException;;

    /**
     * Retrieves recent activities performed by a specific user/agent.
     *
     * @param usernameOrEmail username or email of the person/system
     * @param limit           max number of results
     * @return activities performed by this user
     */
    List<ActivityLogDTO> getActivitiesByUser(String usernameOrEmail, int limit)  throws BadRequestException;;

    /**
     * Deletes old activity logs (maintenance/cleanup).
     * <p>
     * Usually called by a scheduled job (e.g. every month).
     * </p>
     *
     * @param olderThanDays delete entries older than this many days
     * @return number of deleted records
     * @throws BadRequestException 
     */
    long cleanupOldActivities(int olderThanDays) throws BadRequestException;
    
    
    void logActivity(ActivityType type, String title, String description, String performedBy);
}