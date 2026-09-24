package com.app.datadistribution.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.entity.CourseInfoPanelCompetitorBranch;

@Repository
public interface CourseInfoPanelCompetitorBranchRepository extends JpaRepository<CourseInfoPanelCompetitorBranch, UUID> {
    List<CourseInfoPanelCompetitorBranch> findByCompetitorIdAndIsDeletedFalse(UUID competitorId);
    Optional<CourseInfoPanelCompetitorBranch> findByIdAndCompetitorIdAndIsDeletedFalse(UUID id, UUID competitorId);
    void deleteByCompetitorId(UUID competitorId);
}
