package com.app.datadistribution.repository;

import com.app.datadistribution.entity.LeadStatusSentiment;
import com.app.datadistribution.enums.LeadStatus;
import com.app.datadistribution.enums.SentimentCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadStatusSentimentRepository extends JpaRepository<LeadStatusSentiment, UUID> {
    Optional<LeadStatusSentiment> findByLeadStatus(LeadStatus leadStatus);
    List<LeadStatusSentiment> findBySentimentCategory(SentimentCategory sentimentCategory);
}
