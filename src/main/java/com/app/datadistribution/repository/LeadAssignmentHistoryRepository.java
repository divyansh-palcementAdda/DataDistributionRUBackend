package com.app.datadistribution.repository;

import com.app.datadistribution.entity.LeadAssignmentHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadAssignmentHistoryRepository extends JpaRepository<LeadAssignmentHistory, UUID> {
    List<LeadAssignmentHistory> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
