package com.app.datadistribution.repository;

import com.app.datadistribution.entity.LeadStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadStatusHistoryRepository extends JpaRepository<LeadStatusHistory, UUID> {
    List<LeadStatusHistory> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
