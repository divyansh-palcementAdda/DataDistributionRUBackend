package com.app.datadistribution.notification.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.notification.entity.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :recipientId AND n.isDeleted = false ORDER BY n.createdAt DESC")
    Page<Notification> findByRecipientIdAndIsDeletedFalse(@Param("recipientId") UUID recipientId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :recipientId AND n.read = false AND n.isDeleted = false ORDER BY n.createdAt DESC")
    Page<Notification> findByRecipientIdAndReadFalseAndIsDeletedFalse(@Param("recipientId") UUID recipientId, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipient.id = :recipientId AND n.read = false AND n.isDeleted = false")
    long countUnreadByRecipientId(@Param("recipientId") UUID recipientId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipient.id = :recipientId AND n.isDeleted = false")
    long countTotalByRecipientId(@Param("recipientId") UUID recipientId);

    @Query("SELECT n FROM Notification n WHERE n.id = :id AND n.recipient.id = :recipientId AND n.isDeleted = false")
    Optional<Notification> findByIdAndRecipientIdAndIsDeletedFalse(@Param("id") UUID id, @Param("recipientId") UUID recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.recipient.id = :recipientId AND n.read = false AND n.isDeleted = false")
    int markAllAsReadForRecipient(@Param("recipientId") UUID recipientId, @Param("readAt") LocalDateTime readAt);

    boolean existsByIdempotencyKey(String idempotencyKey);
}

