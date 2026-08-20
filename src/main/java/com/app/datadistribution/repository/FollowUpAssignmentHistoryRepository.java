package com.app.datadistribution.repository;

import com.app.datadistribution.entity.FollowUpAssignmentHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FollowUpAssignmentHistoryRepository extends JpaRepository<FollowUpAssignmentHistory, UUID> {
    List<FollowUpAssignmentHistory> findByFollowUpIdOrderByCreatedAtDesc(UUID followUpId);
    List<FollowUpAssignmentHistory> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
