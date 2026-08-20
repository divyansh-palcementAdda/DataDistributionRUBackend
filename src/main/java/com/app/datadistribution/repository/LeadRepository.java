package com.app.datadistribution.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.entity.Lead;

@Repository
public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {
    Optional<Lead> findByLeadCode(String leadCode);
    boolean existsByLeadCode(String leadCode);
    boolean existsByPhoneNumberAndIsDeletedFalse(String phoneNumber);
    List<Lead> findByPhoneNumberInAndIsDeletedFalse(Collection<String> phoneNumbers);

    @Query("SELECT DISTINCT l.phoneNumber FROM Lead l WHERE l.isDeleted = false AND l.phoneNumber IS NOT NULL")
    List<String> findAllActivePhoneNumbers();

    @Query("SELECT l.currentStatus, COUNT(l) FROM Lead l WHERE l.isDeleted = false GROUP BY l.currentStatus")
    List<Object[]> countByStatus();

    @Query("SELECT s, COUNT(l) FROM Lead l JOIN l.leadSources s WHERE l.isDeleted = false AND s.isDeleted = false GROUP BY s")
    List<Object[]> countBySource();

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.isDeleted = false AND l.assignedTo.id = :userId AND l.lastContactedAt IS NULL AND l.feedbacks IS EMPTY AND l.followUps IS EMPTY")
    long countUnavailedLeadsByUserId(@org.springframework.data.repository.query.Param("userId") UUID userId);

    @Query("SELECT l.assignedTo.id, COUNT(l) FROM Lead l WHERE l.isDeleted = false AND l.assignedTo.id IS NOT NULL GROUP BY l.assignedTo.id")
    List<Object[]> findAllottedLeadCountsGroupedByUser();

    @Query("SELECT l.assignedTo.id, COUNT(l) FROM Lead l WHERE l.isDeleted = false AND l.assignedTo.id IS NOT NULL AND l.lastContactedAt IS NULL AND l.feedbacks IS EMPTY AND l.followUps IS EMPTY GROUP BY l.assignedTo.id")
    List<Object[]> findUnavailedLeadCountsGroupedByUser();
}
