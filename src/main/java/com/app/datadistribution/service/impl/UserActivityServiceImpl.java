package com.app.datadistribution.service.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.useractivity.UserDailyActivityResponseDTO;
import com.app.datadistribution.dto.useractivity.UserInactivityPeriodDTO;
import com.app.datadistribution.dto.useractivity.UserSessionHistoryDTO;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAvailed;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.entity.UserActivityPeriod;
import com.app.datadistribution.entity.UserLoginSession;
import com.app.datadistribution.enums.ActivityPeriodType;
import com.app.datadistribution.enums.LogoutReason;
import com.app.datadistribution.enums.SessionStatus;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.LeadAvailedRepository;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserActivityPeriodRepository;
import com.app.datadistribution.repository.UserLoginSessionRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.interfaces.IUserActivityService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityServiceImpl implements IUserActivityService {

    private final UserLoginSessionRepository sessionRepository;
    private final UserActivityPeriodRepository periodRepository;
    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final LeadAvailedRepository availedRepository;
    private final LeadFollowUpRepository followUpRepository;
    private final LeadStatusRepository leadStatusRepository;
    private final IUserDataScopeService dataScopeService;
    private final EntityManager entityManager;

    private static final int INACTIVITY_THRESHOLD_MINUTES = 2; // Gaps >= 2 min between heartbeats count as inactive
    private static final int AUTO_LOGOUT_THRESHOLD_MINUTES = 15; // 15 min continuous inactivity

    @Override
    @Transactional
    public UserLoginSession recordLogin(User user, String ipAddress, String deviceInfo, Long tokenVersion) {
        if (user == null) return null;

        LocalDateTime now = LocalDateTime.now();

        // 1. Close any stale open active sessions for this user
        List<UserLoginSession> staleSessions = sessionRepository.findActiveSessionsForUser(
                user.getId(), List.of(SessionStatus.ACTIVE, SessionStatus.INACTIVE));

        for (UserLoginSession stale : staleSessions) {
            closeSession(stale, now, LogoutReason.SYSTEM_LOGOUT);
        }

        // 2. Create new login session
        UserLoginSession newSession = UserLoginSession.builder()
                .user(user)
                .loginAt(now)
                .lastActivityAt(now)
                .sessionStatus(SessionStatus.ACTIVE)
                .totalActiveDurationSeconds(0L)
                .totalInactiveDurationSeconds(0L)
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .tokenVersion(tokenVersion)
                .build();

        newSession = sessionRepository.save(newSession);

        // 3. Create initial ACTIVE activity period
        UserActivityPeriod initialPeriod = UserActivityPeriod.builder()
                .session(newSession)
                .user(user)
                .periodType(ActivityPeriodType.ACTIVE)
                .startedAt(now)
                .reason("LOGIN")
                .build();

        periodRepository.save(initialPeriod);

        log.info("USER SESSION STARTED | User: {} | SessionId: {} | LoginAt: {}", user.getUsername(), newSession.getId(), now);
        return newSession;
    }

    @Override
    @Transactional
    public void recordHeartbeat(UUID userId) {
        if (userId == null) return;

        LocalDateTime now = LocalDateTime.now();
        Optional<UserLoginSession> activeSessionOpt = sessionRepository.findFirstActiveSessionByUserId(userId);

        if (activeSessionOpt.isEmpty()) {
            return;
        }

        UserLoginSession session = activeSessionOpt.get();
        LocalDateTime lastAct = session.getLastActivityAt();

        if (lastAct != null) {
            long minutesGap = Duration.between(lastAct, now).toMinutes();

            // If gap >= 15 min, session has timed out on backend
            if (minutesGap >= AUTO_LOGOUT_THRESHOLD_MINUTES) {
                closeSession(session, now, LogoutReason.AUTO_LOGOUT_INACTIVITY);
                return;
            }

            // If gap is between 2 min and 15 min, record an INACTIVE period for the gap
            if (minutesGap >= INACTIVITY_THRESHOLD_MINUTES) {
                // Close current open period at lastActivityAt
                periodRepository.findOpenPeriodForSession(session.getId()).ifPresent(p -> {
                    p.setEndedAt(lastAct);
                    p.setDurationSeconds(Duration.between(p.getStartedAt(), lastAct).toSeconds());
                    periodRepository.save(p);
                });

                // Record the INACTIVE period
                long inactiveSecs = Duration.between(lastAct, now).toSeconds();
                UserActivityPeriod inactivePeriod = UserActivityPeriod.builder()
                        .session(session)
                        .user(session.getUser())
                        .periodType(ActivityPeriodType.INACTIVE)
                        .startedAt(lastAct)
                        .endedAt(now)
                        .durationSeconds(inactiveSecs)
                        .reason("BROWSER_INACTIVITY")
                        .build();
                periodRepository.save(inactivePeriod);

                session.setTotalInactiveDurationSeconds(session.getTotalInactiveDurationSeconds() + inactiveSecs);

                // Start new ACTIVE period from now
                UserActivityPeriod newActivePeriod = UserActivityPeriod.builder()
                        .session(session)
                        .user(session.getUser())
                        .periodType(ActivityPeriodType.ACTIVE)
                        .startedAt(now)
                        .reason("HEARTBEAT_RESUMED")
                        .build();
                periodRepository.save(newActivePeriod);
            }
        }

        session.setLastActivityAt(now);
        session.setSessionStatus(SessionStatus.ACTIVE);
        sessionRepository.save(session);
    }

    @Override
    @Transactional
    public void recordLogout(UUID userId, String refreshToken, LogoutReason logoutReason) {
        if (userId == null) return;

        LocalDateTime now = LocalDateTime.now();
        List<UserLoginSession> activeSessions = sessionRepository.findActiveSessionsForUser(
                userId, List.of(SessionStatus.ACTIVE, SessionStatus.INACTIVE));

        for (UserLoginSession session : activeSessions) {
            closeSession(session, now, logoutReason != null ? logoutReason : LogoutReason.MANUAL_LOGOUT);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDailyActivityResponseDTO getDailyActivity(UUID targetUserId, LocalDate date)
            throws UnauthorizedException, AccessDeniedException, BadRequestException, ResourcesNotFoundException {
        if (targetUserId == null) throw new BadRequestException("Target userId is required");
        LocalDate effectiveDate = date != null ? date : LocalDate.now();

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with id: " + targetUserId));

        validateUserAccess(targetUserId);

        LocalDateTime startOfDay = effectiveDate.atStartOfDay();
        LocalDateTime endOfDay = effectiveDate.atTime(LocalTime.MAX);
        LocalDateTime now = LocalDateTime.now();

        // 1. Fetch Sessions on date
        List<UserLoginSession> sessions = sessionRepository.findSessionsForUserOnDate(targetUserId, startOfDay, endOfDay);
        List<UserActivityPeriod> periods = periodRepository.findPeriodsForUserOnDate(targetUserId, startOfDay, endOfDay);

        LocalDateTime firstLoginAt = sessions.isEmpty() ? null : sessions.get(0).getLoginAt();
        LocalDateTime lastLogoutAt = null;

        for (int i = sessions.size() - 1; i >= 0; i--) {
            if (sessions.get(i).getLogoutAt() != null) {
                lastLogoutAt = sessions.get(i).getLogoutAt();
                break;
            }
        }

        // 2. Determine Current Status
        String currentStatus = "LOGGED_OUT";
        Long currentInactiveMinutes = null;
        LocalDateTime latestActivityAt = null;

        List<UserLoginSession> latestSessions = sessionRepository.findLatestSessionsForUser(targetUserId);
        if (!latestSessions.isEmpty()) {
            UserLoginSession latest = latestSessions.get(0);
            latestActivityAt = latest.getLastActivityAt();

            if (latest.getLogoutAt() == null && latest.getSessionStatus() == SessionStatus.ACTIVE) {
                long minutesSinceLastAct = Duration.between(latest.getLastActivityAt(), now).toMinutes();
                if (minutesSinceLastAct < INACTIVITY_THRESHOLD_MINUTES) {
                    currentStatus = "WORKING";
                } else if (minutesSinceLastAct < AUTO_LOGOUT_THRESHOLD_MINUTES) {
                    currentStatus = "INACTIVE";
                    currentInactiveMinutes = minutesSinceLastAct;
                } else {
                    currentStatus = "LOGGED_OUT";
                }
            }
        }

        // 3. Calculate Working and Inactive Durations
        long totalInactiveSeconds = 0;
        int inactiveCount = 0;

        for (UserActivityPeriod p : periods) {
            if (p.getPeriodType() == ActivityPeriodType.INACTIVE) {
                inactiveCount++;
                long duration = p.getDurationSeconds() != null ? p.getDurationSeconds() : 0L;
                if (p.getEndedAt() == null) {
                    duration = Duration.between(p.getStartedAt(), now).toSeconds();
                }
                totalInactiveSeconds += duration;
            }
        }

        long totalSessionSeconds = 0;
        for (UserLoginSession s : sessions) {
            LocalDateTime end = s.getLogoutAt() != null ? s.getLogoutAt() : (effectiveDate.isEqual(LocalDate.now()) ? now : endOfDay);
            if (end.isAfter(s.getLoginAt())) {
                totalSessionSeconds += Duration.between(s.getLoginAt(), end).toSeconds();
            }
        }

        long totalWorkingSeconds = Math.max(0, totalSessionSeconds - totalInactiveSeconds);
        long totalWorkingMinutes = totalWorkingSeconds / 60;
        long totalInactiveMinutes = totalInactiveSeconds / 60;

        // 4. CRM Productivity Metrics for that date
        long totalAvailed = countAvailedOnDate(user, startOfDay, endOfDay);
        long totalFollowUpsTaken = countFollowUpsTakenOnDate(user, startOfDay, endOfDay);
        long totalFollowUpsScheduled = countFollowUpsScheduledOnDate(user, startOfDay, endOfDay);
        long totalFollowUpsMissed = countFollowUpsMissedOnDate(user, effectiveDate, startOfDay, endOfDay);
        long totalConnected = countConnectedLeads(user, startOfDay, endOfDay);
        long totalNotConnected = countNotConnectedLeads(user, startOfDay, endOfDay);

        String fullName = (user.getFirstName() != null ? user.getFirstName() : "") +
                (user.getLastName() != null ? " " + user.getLastName() : "");
        fullName = fullName.trim();
        if (fullName.isEmpty()) fullName = user.getUsername();

        String departmentName = user.getDepartments() != null && !user.getDepartments().isEmpty()
                ? user.getDepartments().stream().map(Department::getName).collect(Collectors.joining(", "))
                : null;

        return UserDailyActivityResponseDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(fullName)
                .email(user.getEmail())
                .department(departmentName)
                .date(effectiveDate)
                .currentStatus(currentStatus)
                .inactiveMinutesCurrent(currentInactiveMinutes)
                .lastActivityAt(latestActivityAt)
                .firstLoginAt(firstLoginAt)
                .lastLogoutAt(lastLogoutAt)
                .loginCount(sessions.size())
                .inactiveCount(inactiveCount)
                .totalWorkingMinutes(totalWorkingMinutes)
                .totalWorkingSeconds(totalWorkingSeconds)
                .totalInactiveMinutes(totalInactiveMinutes)
                .totalInactiveSeconds(totalInactiveSeconds)
                .formattedWorkingHours(formatDurationHoursMinutes(totalWorkingSeconds))
                .formattedInactiveDuration(formatDurationHoursMinutes(totalInactiveSeconds))
                .totalAvailed(totalAvailed)
                .totalFollowUpsTaken(totalFollowUpsTaken)
                .totalFollowUpsScheduled(totalFollowUpsScheduled)
                .totalFollowUpsMissed(totalFollowUpsMissed)
                .totalConnected(totalConnected)
                .totalNotConnected(totalNotConnected)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSessionHistoryDTO> getSessionHistory(UUID targetUserId, LocalDate date)
            throws UnauthorizedException, AccessDeniedException, BadRequestException, ResourcesNotFoundException {
        if (targetUserId == null) throw new BadRequestException("Target userId is required");
        LocalDate effectiveDate = date != null ? date : LocalDate.now();

        validateUserAccess(targetUserId);

        LocalDateTime startOfDay = effectiveDate.atStartOfDay();
        LocalDateTime endOfDay = effectiveDate.atTime(LocalTime.MAX);
        LocalDateTime now = LocalDateTime.now();

        List<UserLoginSession> sessions = sessionRepository.findSessionsForUserOnDate(targetUserId, startOfDay, endOfDay);
        List<UserSessionHistoryDTO> result = new ArrayList<>();

        for (UserLoginSession s : sessions) {
            LocalDateTime end = s.getLogoutAt() != null ? s.getLogoutAt() : (effectiveDate.isEqual(LocalDate.now()) ? now : endOfDay);
            long sessionSecs = Duration.between(s.getLoginAt(), end).toSeconds();
            long inactiveSecs = s.getTotalInactiveDurationSeconds();
            long workingSecs = Math.max(0, sessionSecs - inactiveSecs);

            result.add(UserSessionHistoryDTO.builder()
                    .sessionId(s.getId())
                    .loginAt(s.getLoginAt())
                    .logoutAt(s.getLogoutAt())
                    .lastActivityAt(s.getLastActivityAt())
                    .workingDurationMinutes(workingSecs / 60)
                    .workingDurationSeconds(workingSecs)
                    .inactiveDurationMinutes(inactiveSecs / 60)
                    .inactiveDurationSeconds(inactiveSecs)
                    .formattedWorkingDuration(formatDurationHoursMinutes(workingSecs))
                    .formattedInactiveDuration(formatDurationHoursMinutes(inactiveSecs))
                    .logoutReason(s.getLogoutReason() != null ? s.getLogoutReason().name() : null)
                    .sessionStatus(s.getSessionStatus() != null ? s.getSessionStatus().name() : "ACTIVE")
                    .ipAddress(s.getIpAddress())
                    .deviceInfo(s.getDeviceInfo())
                    .build());
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserInactivityPeriodDTO> getInactivityHistory(UUID targetUserId, LocalDate date)
            throws UnauthorizedException, AccessDeniedException, BadRequestException, ResourcesNotFoundException {
        if (targetUserId == null) throw new BadRequestException("Target userId is required");
        LocalDate effectiveDate = date != null ? date : LocalDate.now();

        validateUserAccess(targetUserId);

        LocalDateTime startOfDay = effectiveDate.atStartOfDay();
        LocalDateTime endOfDay = effectiveDate.atTime(LocalTime.MAX);
        LocalDateTime now = LocalDateTime.now();

        List<UserActivityPeriod> periods = periodRepository.findPeriodsForUserOnDateByType(
                targetUserId, ActivityPeriodType.INACTIVE, startOfDay, endOfDay);

        List<UserInactivityPeriodDTO> result = new ArrayList<>();
        for (UserActivityPeriod p : periods) {
            long duration = p.getDurationSeconds() != null ? p.getDurationSeconds() : 0L;
            if (p.getEndedAt() == null) {
                duration = Duration.between(p.getStartedAt(), now).toSeconds();
            }

            result.add(UserInactivityPeriodDTO.builder()
                    .periodId(p.getId())
                    .sessionId(p.getSession() != null ? p.getSession().getId() : null)
                    .startedAt(p.getStartedAt())
                    .endedAt(p.getEndedAt())
                    .durationMinutes(duration / 60)
                    .durationSeconds(duration)
                    .formattedDuration(formatDurationHoursMinutes(duration))
                    .reason(p.getReason())
                    .build());
        }

        return result;
    }

    // =========================================================================
    // Private Helpers & CRM Metric Queries
    // =========================================================================

    private void closeSession(UserLoginSession session, LocalDateTime logoutTime, LogoutReason logoutReason) {
        if (session == null || session.getLogoutAt() != null) return;

        // Close any open period
        periodRepository.findOpenPeriodForSession(session.getId()).ifPresent(p -> {
            p.setEndedAt(logoutTime);
            long dur = Duration.between(p.getStartedAt(), logoutTime).toSeconds();
            p.setDurationSeconds(dur);
            periodRepository.save(p);
        });

        if (logoutReason == LogoutReason.AUTO_LOGOUT_INACTIVITY) {
            // Record final 15-minute inactivity period
            LocalDateTime inactiveStart = session.getLastActivityAt() != null ? session.getLastActivityAt() : logoutTime.minusMinutes(15);
            long inactiveSecs = Duration.between(inactiveStart, logoutTime).toSeconds();

            UserActivityPeriod finalInactive = UserActivityPeriod.builder()
                    .session(session)
                    .user(session.getUser())
                    .periodType(ActivityPeriodType.INACTIVE)
                    .startedAt(inactiveStart)
                    .endedAt(logoutTime)
                    .durationSeconds(inactiveSecs)
                    .reason("AUTO_LOGOUT_INACTIVITY")
                    .build();
            periodRepository.save(finalInactive);

            session.setTotalInactiveDurationSeconds(session.getTotalInactiveDurationSeconds() + inactiveSecs);
        }

        session.setLogoutAt(logoutTime);
        session.setLogoutReason(logoutReason);
        session.setSessionStatus(SessionStatus.LOGGED_OUT);

        long totalSecs = Duration.between(session.getLoginAt(), logoutTime).toSeconds();
        session.setTotalActiveDurationSeconds(Math.max(0, totalSecs - session.getTotalInactiveDurationSeconds()));

        sessionRepository.save(session);
        log.info("USER SESSION CLOSED | User: {} | SessionId: {} | Reason: {}", session.getUser().getUsername(), session.getId(), logoutReason);
    }

    private void validateUserAccess(UUID targetUserId) throws UnauthorizedException, AccessDeniedException, BadRequestException {
        UserDataScope scope = dataScopeService.getScopeForCurrentUser();
        if (scope == null) return;

        if (scope.getScopeType() == ScopeType.SYSTEM) {
            return;
        }

        if (scope.getScopeType() == ScopeType.SELF) {
            if (!targetUserId.equals(scope.getUserId())) {
                throw new AccessDeniedException("You are not authorized to view another user's activity.");
            }
            return;
        }

        if (scope.getScopeType() == ScopeType.DEPARTMENT) {
            if (targetUserId.equals(scope.getUserId())) return;
            if (scope.getDepartmentUserIds() != null && scope.getDepartmentUserIds().contains(targetUserId)) {
                return;
            }
            throw new AccessDeniedException("Target user does not belong to your department data scope.");
        }
    }

    private long countAvailedOnDate(User user, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<LeadAvailed> root = query.from(LeadAvailed.class);

        query.select(cb.countDistinct(root.get("lead").get("id"))).where(
                cb.equal(root.get("availedByUser").get("id"), user.getId()),
                cb.between(root.get("availedAt"), startOfDay, endOfDay),
                cb.equal(root.get("isDeleted"), false)
        );

        return entityManager.createQuery(query).getSingleResult();
    }

    private long countFollowUpsTakenOnDate(User user, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<LeadFollowUp> root = query.from(LeadFollowUp.class);

        query.select(cb.count(root.get("id"))).where(
                cb.equal(root.get("assignedTo").get("id"), user.getId()),
                cb.equal(root.get("completed"), true),
                cb.between(root.get("completedAt"), startOfDay, endOfDay),
                cb.equal(root.get("isDeleted"), false)
        );

        return entityManager.createQuery(query).getSingleResult();
    }

    private long countFollowUpsScheduledOnDate(User user, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<LeadFollowUp> root = query.from(LeadFollowUp.class);

        query.select(cb.count(root.get("id"))).where(
                cb.equal(root.get("assignedTo").get("id"), user.getId()),
                cb.between(root.get("followUpDate"), startOfDay, endOfDay),
                cb.equal(root.get("isDeleted"), false)
        );

        return entityManager.createQuery(query).getSingleResult();
    }

    private long countFollowUpsMissedOnDate(User user, LocalDate date, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<LeadFollowUp> root = query.from(LeadFollowUp.class);

        LocalDateTime now = LocalDateTime.now();
        Predicate missedCondition;

        if (date.isEqual(LocalDate.now())) {
            // Scheduled for today but time has passed and not completed
            missedCondition = cb.and(
                    cb.between(root.get("followUpDate"), startOfDay, endOfDay),
                    cb.lessThan(root.get("followUpDate"), now),
                    cb.equal(root.get("completed"), false)
            );
        } else if (date.isBefore(LocalDate.now())) {
            // Past date: scheduled on that date and not completed or completed after that date
            missedCondition = cb.and(
                    cb.between(root.get("followUpDate"), startOfDay, endOfDay),
                    cb.or(
                            cb.equal(root.get("completed"), false),
                            cb.greaterThan(root.get("completedAt"), endOfDay)
                    )
            );
        } else {
            // Future date: not missed
            return 0L;
        }

        query.select(cb.count(root.get("id"))).where(
                cb.equal(root.get("assignedTo").get("id"), user.getId()),
                missedCondition,
                cb.equal(root.get("isDeleted"), false)
        );

        return entityManager.createQuery(query).getSingleResult();
    }

    private long countConnectedLeads(User user, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        Set<UUID> connectedStatusIds = getDescendantStatusIds("CONNECTED");
        if (connectedStatusIds.isEmpty()) return 0L;

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Lead> root = query.from(Lead.class);

        query.select(cb.countDistinct(root.get("id"))).where(
                cb.equal(root.get("assignedTo").get("id"), user.getId()),
                root.get("currentStatus").get("id").in(connectedStatusIds),
                cb.equal(root.get("isDeleted"), false)
        );

        return entityManager.createQuery(query).getSingleResult();
    }

    private long countNotConnectedLeads(User user, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        Set<UUID> notConnectedStatusIds = getDescendantStatusIds("NOT_CONNECTED");
        if (notConnectedStatusIds.isEmpty()) return 0L;

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Lead> root = query.from(Lead.class);

        query.select(cb.countDistinct(root.get("id"))).where(
                cb.equal(root.get("assignedTo").get("id"), user.getId()),
                root.get("currentStatus").get("id").in(notConnectedStatusIds),
                cb.equal(root.get("isDeleted"), false)
        );

        return entityManager.createQuery(query).getSingleResult();
    }

    /**
     * Dynamically collects all status IDs under the given root code (e.g. CONNECTED, NOT_CONNECTED)
     */
    private Set<UUID> getDescendantStatusIds(String rootCode) {
        List<LeadStatus> allStatuses = leadStatusRepository.findAll();
        Set<UUID> collected = new HashSet<>();

        Optional<LeadStatus> rootStatus = allStatuses.stream()
                .filter(s -> !s.isDeleted() && rootCode.equalsIgnoreCase(s.getCode()))
                .findFirst();

        if (rootStatus.isPresent()) {
            collected.add(rootStatus.get().getId());
            boolean addedNew;
            do {
                addedNew = false;
                for (LeadStatus s : allStatuses) {
                    if (!s.isDeleted() && !collected.contains(s.getId())) {
                        if (s.getParentStatus() != null && collected.contains(s.getParentStatus().getId())) {
                            collected.add(s.getId());
                            addedNew = true;
                        }
                    }
                }
            } while (addedNew);
        }

        // Also add statuses where code starts with rootCode (e.g. NOT_CONNECTED_1, NOT_CONNECTED_2, etc.)
        for (LeadStatus s : allStatuses) {
            if (!s.isDeleted() && s.getCode() != null) {
                if (s.getCode().toUpperCase().startsWith(rootCode.toUpperCase()) ||
                    s.getCode().toUpperCase().endsWith(rootCode.toUpperCase())) {
                    collected.add(s.getId());
                }
            }
        }

        return collected;
    }

    private String formatDurationHoursMinutes(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return String.format("%02dh %02dm", hours, minutes);
    }
}
