package com.app.datadistribution.repository;

import com.app.datadistribution.entity.LeadFollowUp;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface LeadFollowUpRepository extends JpaRepository<LeadFollowUp, UUID>, JpaSpecificationExecutor<LeadFollowUp> {
    List<LeadFollowUp> findByLeadIdOrderByFollowUpDateDesc(UUID leadId);

    @Query("SELECT COUNT(f) FROM LeadFollowUp f WHERE f.isDeleted = false AND (f.assignedTo.id = :userId OR (f.assignedTo.id IS NULL AND f.lead.assignedTo.id = :userId)) AND f.followUpDate >= :startOfDay AND f.followUpDate <= :endOfDay")
    long countScheduledFollowUpsForUserBetween(@Param("userId") UUID userId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COALESCE(f.assignedTo.id, f.lead.assignedTo.id), COUNT(f) FROM LeadFollowUp f WHERE f.isDeleted = false AND (f.assignedTo.id IS NOT NULL OR f.lead.assignedTo.id IS NOT NULL) AND f.followUpDate >= :startOfDay AND f.followUpDate <= :endOfDay GROUP BY COALESCE(f.assignedTo.id, f.lead.assignedTo.id)")
    List<Object[]> countScheduledFollowUpsGroupedByUserBetween(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COALESCE(f.assignedTo.id, f.lead.assignedTo.id), MIN(f.followUpDate) FROM LeadFollowUp f WHERE f.isDeleted = false AND (f.assignedTo.id IS NOT NULL OR f.lead.assignedTo.id IS NOT NULL) AND f.followUpDate >= :startOfDay AND f.followUpDate <= :endOfDay GROUP BY COALESCE(f.assignedTo.id, f.lead.assignedTo.id)")
    List<Object[]> findEarliestScheduledFollowUpGroupedByUserBetween(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT f FROM LeadFollowUp f WHERE f.isDeleted = false AND f.completed = false AND f.lead.id IN :leadIds AND f.followUpDate >= :startOfDay")
    List<LeadFollowUp> findPendingUncompletedFollowUpsByLeadIds(@Param("leadIds") List<UUID> leadIds, @Param("startOfDay") LocalDateTime startOfDay);
}

