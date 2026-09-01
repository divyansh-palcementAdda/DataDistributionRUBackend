package com.app.datadistribution.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.entity.EmailLog;
import com.app.datadistribution.enums.EmailStatus;
import com.app.datadistribution.enums.EmailType;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<EmailLog> findByIdempotencyKey(String idempotencyKey);

    Page<EmailLog> findByRecipientUserId(UUID recipientUserId, Pageable pageable);

    Page<EmailLog> findByEmailType(EmailType emailType, Pageable pageable);

    Page<EmailLog> findByStatus(EmailStatus status, Pageable pageable);

    @Query("SELECT e FROM EmailLog e WHERE "
         + "(:status IS NULL OR e.status = :status) AND "
         + "(:emailType IS NULL OR e.emailType = :emailType) AND "
         + "(:search IS NULL OR LOWER(e.recipientEmail) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(e.recipientName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(e.subject) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<EmailLog> searchLogs(
            @Param("status") EmailStatus status,
            @Param("emailType") EmailType emailType,
            @Param("search") String search,
            Pageable pageable);
}
