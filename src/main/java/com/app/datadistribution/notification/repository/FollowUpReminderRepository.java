package com.app.datadistribution.notification.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.notification.entity.FollowUpReminder;
import com.app.datadistribution.notification.enums.ReminderStatus;

@Repository
public interface FollowUpReminderRepository extends JpaRepository<FollowUpReminder, UUID> {

    @Modifying
    @Query("UPDATE FollowUpReminder r SET r.status = 'CANCELLED' WHERE r.followUpId = :followUpId AND r.status = 'PENDING'")
    int cancelPendingRemindersForFollowUp(@Param("followUpId") UUID followUpId);

    @Modifying
    @Query("UPDATE FollowUpReminder r SET r.status = 'CANCELLED' WHERE r.followUpId IN (:followUpIds) AND r.status = 'PENDING'")
    int cancelPendingRemindersForFollowUps(@Param("followUpIds") Collection<UUID> followUpIds);

    @Modifying
    @Query("UPDATE FollowUpReminder r SET r.recipientUserId = :newUserId WHERE r.followUpId = :followUpId AND r.status = 'PENDING'")
    int reassignPendingReminders(@Param("followUpId") UUID followUpId, @Param("newUserId") UUID newUserId);

    /**
     * Concurrency-safe atomic claim query for cluster/multi-instance environments:
     * Claims up to batch of pending reminders that are due for dispatch.
     */
    @Modifying
    @Query("UPDATE FollowUpReminder r SET r.status = 'DISPATCHING', r.claimedAt = :now, r.claimedBy = :claimedBy " +
           "WHERE r.status = 'PENDING' AND r.scheduledFor <= :dueTime")
    int claimDueReminders(@Param("dueTime") LocalDateTime dueTime, @Param("now") LocalDateTime now, @Param("claimedBy") String claimedBy);

    List<FollowUpReminder> findByStatusAndClaimedBy(ReminderStatus status, String claimedBy);

    boolean existsByIdempotencyKey(String idempotencyKey);
}

