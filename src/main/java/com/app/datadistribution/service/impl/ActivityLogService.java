package com.app.datadistribution.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.ActivityLogDTO;
import com.app.datadistribution.entity.ActivityLog;
import com.app.datadistribution.enums.ActivityType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.repository.ActivityLogRepository;
import com.app.datadistribution.service.interfaces.IActivityLogService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService implements IActivityLogService {

    private final ActivityLogRepository repository;

    @Override
    @Transactional
    public void logActivity(ActivityType type, String description, String performedBy) {
        ActivityLog activityLog = ActivityLog.builder()
                .activityType(type)
                .description(description)
                .performedBy(performedBy != null ? performedBy : "System")
                .build();
        repository.save(activityLog);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityLogDTO> getRecentActivities(int limit) {
        int finalLimit = (limit > 0) ? limit : 20;
        List<ActivityLog> logs = repository.findRecentExcludingType(ActivityType.TOKEN_REFRESH,
                PageRequest.of(0, finalLimit));
        return logs.stream().map(this::toDto).toList();
    }

    private ActivityLogDTO toDto(ActivityLog activityLog) {
        return ActivityLogDTO.builder()
                .icon(activityLog.getActivityType().getIcon())
                .iconBg(activityLog.getActivityType().getIconBg())
                .title(activityLog.getTitle())
                .desc(activityLog.getDescription())
                .by("by " + activityLog.getPerformedBy())
                .time(getHumanReadableTime(activityLog.getCreatedAt()))
                .createdAt(activityLog.getCreatedAt())
                .build();
    }

    private String getHumanReadableTime(LocalDateTime time) {
        if (time == null) return "Unknown";
        Duration duration = Duration.between(time, LocalDateTime.now());
        long seconds = duration.getSeconds();

        if (seconds < 60) return "just now";
        if (seconds < 3600) return (seconds / 60) + " mins ago";
        if (seconds < 86400) return (seconds / 3600) + " hours ago";
        if (seconds < 2592000) return (seconds / 86400) + " days ago";
        return time.toString();
    }

    @Override
    @Transactional
    public void logActivity(ActivityType type, String description) {
        String performedBy = resolveCurrentUser();
        logActivity(type, description, performedBy);
    }

    private String resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "SYSTEM";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if ("anonymousUser".equals(principal)) {
            return "SYSTEM";
        }
        return auth.getName();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityLogDTO> getRecentActivitiesByType(ActivityType type, int limit) throws BadRequestException {
        if (type == null) {
            throw new BadRequestException("Activity type must not be null");
        }
        int finalLimit = (limit > 0) ? limit : 20;
        List<ActivityLog> logs = repository.findByActivityTypeOrderByCreatedAtDesc(
                type, PageRequest.of(0, finalLimit)
        );
        return logs.stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityLogDTO> getActivitiesByUser(String usernameOrEmail, int limit) throws BadRequestException {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            throw new BadRequestException("Username or email must not be empty");
        }
        int finalLimit = (limit > 0) ? limit : 20;
        List<ActivityLog> logs = repository.findByUser(
                usernameOrEmail.trim(), PageRequest.of(0, finalLimit)
        );
        return logs.stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public long cleanupOldActivities(int olderThanDays) throws BadRequestException {
        if (olderThanDays <= 0) {
            throw new BadRequestException("Days must be greater than 0");
        }
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(olderThanDays);
        int deletedCount = repository.deleteOldActivities(cutoffDate);
        log.info("Deleted {} activity logs older than {} days", deletedCount, olderThanDays);
        return deletedCount;
    }

    @Override
    @Transactional
    public void logActivity(ActivityType type, String title, String description, String performedBy) {
        ActivityLog logEntry = ActivityLog.builder()
                .activityType(type)
                .title(title != null ? title : type.getDefaultTitle())
                .description(description)
                .performedBy(performedBy)
                .build();
        repository.save(logEntry);
        log.debug("Activity logged: {} by {}", type, performedBy);
    }
}