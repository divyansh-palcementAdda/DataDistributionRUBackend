package com.app.datadistribution.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.entity.CompetitorCourseComparison;

@Repository
public interface CompetitorCourseComparisonRepository extends JpaRepository<CompetitorCourseComparison, UUID> {
    Optional<CompetitorCourseComparison> findByCompetitorIdAndIsDeletedFalse(UUID competitorId);
    Optional<CompetitorCourseComparison> findByIdAndCompetitorIdAndIsDeletedFalse(UUID id, UUID competitorId);
    void deleteByCompetitorId(UUID competitorId);
}
