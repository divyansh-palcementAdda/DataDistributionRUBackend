package com.app.datadistribution.repository;

import com.app.datadistribution.entity.LeadFollowUp;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadFollowUpRepository extends JpaRepository<LeadFollowUp, UUID>, JpaSpecificationExecutor<LeadFollowUp> {
    List<LeadFollowUp> findByLeadIdOrderByFollowUpDateDesc(UUID leadId);
}

