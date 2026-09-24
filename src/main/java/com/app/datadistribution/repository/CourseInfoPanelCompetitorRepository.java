package com.app.datadistribution.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.entity.CourseInfoPanelCompetitor;

@Repository
public interface CourseInfoPanelCompetitorRepository extends JpaRepository<CourseInfoPanelCompetitor, UUID> {
    List<CourseInfoPanelCompetitor> findByInfoPanelIdAndIsDeletedFalseOrderByDisplayOrderAsc(UUID infoPanelId);
    List<CourseInfoPanelCompetitor> findByInfoPanelIdAndActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc(UUID infoPanelId);
    Optional<CourseInfoPanelCompetitor> findByIdAndInfoPanelIdAndIsDeletedFalse(UUID id, UUID infoPanelId);
}
