package com.app.datadistribution.repository;

import com.app.datadistribution.entity.LeadFollowUp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadFollowUpRepository extends JpaRepository<LeadFollowUp, UUID>, JpaSpecificationExecutor<LeadFollowUp> {

    @Override
    @EntityGraph(attributePaths = {"lead", "lead.currentStatus", "createdByUser"})
    Page<LeadFollowUp> findAll(@Nullable Specification<LeadFollowUp> spec, Pageable pageable);

    List<LeadFollowUp> findByLeadIdOrderByFollowUpDateDesc(UUID leadId);

    @Query("SELECT COUNT(f) FROM LeadFollowUp f WHERE f.isDeleted = false AND (f.assignedTo.id = :userId OR (f.assignedTo.id IS NULL AND f.lead.assignedTo.id = :userId)) AND f.followUpDate >= :startOfDay AND f.followUpDate <= :endOfDay")
    long countScheduledFollowUpsForUserBetween(@Param("userId") UUID userId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COALESCE(f.assignedTo.id, f.lead.assignedTo.id), COUNT(f) FROM LeadFollowUp f WHERE f.isDeleted = false AND (f.assignedTo.id IS NOT NULL OR f.lead.assignedTo.id IS NOT NULL) AND f.followUpDate >= :startOfDay AND f.followUpDate <= :endOfDay GROUP BY COALESCE(f.assignedTo.id, f.lead.assignedTo.id)")
    List<Object[]> countScheduledFollowUpsGroupedByUserBetween(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COALESCE(f.assignedTo.id, f.lead.assignedTo.id), MIN(f.followUpDate) FROM LeadFollowUp f WHERE f.isDeleted = false AND (f.assignedTo.id IS NOT NULL OR f.lead.assignedTo.id IS NOT NULL) AND f.followUpDate >= :startOfDay AND f.followUpDate <= :endOfDay GROUP BY COALESCE(f.assignedTo.id, f.lead.assignedTo.id)")
    List<Object[]> findEarliestScheduledFollowUpGroupedByUserBetween(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT f FROM LeadFollowUp f WHERE f.isDeleted = false AND f.completed = false AND f.lead.id IN :leadIds AND f.followUpDate >= :startOfDay")
    List<LeadFollowUp> findPendingUncompletedFollowUpsByLeadIds(@Param("leadIds") List<UUID> leadIds, @Param("startOfDay") LocalDateTime startOfDay);

    @Query("SELECT COUNT(f) > 0 FROM LeadFollowUp f WHERE f.isDeleted = false AND f.lead.id = :leadId AND f.status IN (com.app.datadistribution.enums.FollowUpStatus.PENDING, com.app.datadistribution.enums.FollowUpStatus.UPCOMING)")
    boolean existsActiveFollowUpByLeadId(@Param("leadId") UUID leadId);

    @Query("SELECT f FROM LeadFollowUp f WHERE f.isDeleted = false AND f.lead.id = :leadId AND f.status IN (com.app.datadistribution.enums.FollowUpStatus.PENDING, com.app.datadistribution.enums.FollowUpStatus.UPCOMING)")
    List<LeadFollowUp> findActiveFollowUpsByLeadId(@Param("leadId") UUID leadId);

    @Query("SELECT MIN(f.followUpDate) FROM LeadFollowUp f WHERE f.isDeleted = false AND f.lead.id = :leadId AND f.status IN (com.app.datadistribution.enums.FollowUpStatus.PENDING, com.app.datadistribution.enums.FollowUpStatus.UPCOMING)")
    LocalDateTime findEarliestActiveFollowUpDateByLeadId(@Param("leadId") UUID leadId);

    @Query("SELECT f FROM LeadFollowUp f "
         + "JOIN FETCH f.lead l "
         + "LEFT JOIN FETCH l.currentStatus "
         + "LEFT JOIN FETCH l.course "
         + "LEFT JOIN FETCH f.assignedTo "
         + "LEFT JOIN FETCH l.assignedTo "
         + "WHERE f.isDeleted = false AND l.isDeleted = false "
         + "AND f.completed = false "
         + "AND f.status IN (com.app.datadistribution.enums.FollowUpStatus.PENDING, com.app.datadistribution.enums.FollowUpStatus.UPCOMING) "
         + "AND f.followUpDate >= :startOfDay AND f.followUpDate <= :endOfDay "
         + "ORDER BY f.followUpDate ASC")
    List<LeadFollowUp> findActiveFollowUpsForDateRangeWithDetails(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);
}


