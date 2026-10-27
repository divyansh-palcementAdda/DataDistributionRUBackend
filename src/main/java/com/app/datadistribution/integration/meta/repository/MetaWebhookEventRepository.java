package com.app.datadistribution.integration.meta.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.integration.meta.entity.MetaWebhookEvent;
import com.app.datadistribution.integration.meta.entity.MetaWebhookEventStatus;

@Repository
public interface MetaWebhookEventRepository extends JpaRepository<MetaWebhookEvent, UUID> {

    Optional<MetaWebhookEvent> findByLeadgenId(String leadgenId);

    boolean existsByLeadgenId(String leadgenId);

    @Query("SELECT e FROM MetaWebhookEvent e WHERE e.isDeleted = false " +
           "AND (e.status = 'PENDING' OR (e.status = 'FAILED' AND e.retryCount < e.maxRetries AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now))) " +
           "ORDER BY e.createdAt ASC")
    List<MetaWebhookEvent> findRetryableEvents(@Param("now") LocalDateTime now);

    List<MetaWebhookEvent> findAllByStatusInAndIsDeletedFalse(List<MetaWebhookEventStatus> statuses);

    long countByStatus(MetaWebhookEventStatus status);
}
