package com.app.datadistribution.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.entity.LeadAvailed;

@Repository
public interface LeadAvailedRepository extends JpaRepository<LeadAvailed, UUID>, JpaSpecificationExecutor<LeadAvailed> {

    Optional<LeadAvailed> findByLeadIdAndAvailedByUserIdAndIsDeletedFalse(UUID leadId, UUID userId);

    List<LeadAvailed> findByLeadIdAndIsDeletedFalseOrderByAvailedAtDesc(UUID leadId);

    boolean existsByLeadIdAndAvailedByUserIdAndIsDeletedFalse(UUID leadId, UUID userId);

    @Query("SELECT la FROM LeadAvailed la WHERE la.lead.id IN :leadIds AND la.isDeleted = false")
    List<LeadAvailed> findByLeadIdInAndIsDeletedFalse(@Param("leadIds") Collection<UUID> leadIds);

    @Query("SELECT COUNT(la) FROM LeadAvailed la WHERE la.lead.id = :leadId AND la.availedByUser.id = :userId AND la.isDeleted = false")
    long countByLeadIdAndUserId(@Param("leadId") UUID leadId, @Param("userId") UUID userId);
}
