package com.app.datadistribution.repository;

import com.app.datadistribution.entity.LeadFeedback;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadFeedbackRepository extends JpaRepository<LeadFeedback, UUID>, JpaSpecificationExecutor<LeadFeedback> {
    List<LeadFeedback> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
}

