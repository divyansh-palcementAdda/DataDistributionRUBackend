package com.app.datadistribution.repository;

import com.app.datadistribution.entity.LeadStatusHistory;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadStatusHistoryRepository extends JpaRepository<LeadStatusHistory, UUID> {
    List<LeadStatusHistory> findByLeadId(UUID leadId);
    List<LeadStatusHistory> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
    List<LeadStatusHistory> findByLeadIdOrderByCreatedAtDescIdDesc(UUID leadId);
    List<LeadStatusHistory> findByLeadIdOrderByCreatedAtAscIdAsc(UUID leadId);
    Page<LeadStatusHistory> findByLeadId(UUID leadId, Pageable pageable);

    @Query("SELECT h FROM LeadStatusHistory h WHERE h.lead.id IN :leadIds AND h.isDeleted = false ORDER BY h.createdAt DESC, h.id DESC")
    List<LeadStatusHistory> findByLeadIdInOrderByCreatedAtDescIdDesc(@Param("leadIds") Collection<UUID> leadIds);
}
