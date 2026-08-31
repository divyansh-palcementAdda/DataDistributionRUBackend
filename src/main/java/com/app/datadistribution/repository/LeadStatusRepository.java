package com.app.datadistribution.repository;

import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.enums.SentimentCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadStatusRepository extends JpaRepository<LeadStatus, UUID>, JpaSpecificationExecutor<LeadStatus> {
    Optional<LeadStatus> findByNameIgnoreCase(String name);
    Optional<LeadStatus> findByCodeIgnoreCase(String code);
    Optional<LeadStatus> findByCode(String code);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
    List<LeadStatus> findBySentimentCategory(SentimentCategory sentimentCategory);
    List<LeadStatus> findByActiveTrueAndIsFollowUpStatusTrueAndIsDeletedFalseOrderByDisplayOrderAsc();
    List<LeadStatus> findByIsFollowUpStatusTrueAndIsDeletedFalseOrderByDisplayOrderAsc();
}
