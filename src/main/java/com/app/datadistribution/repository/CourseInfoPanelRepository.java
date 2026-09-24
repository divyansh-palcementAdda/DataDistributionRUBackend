package com.app.datadistribution.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.entity.CourseInfoPanel;

@Repository
public interface CourseInfoPanelRepository extends JpaRepository<CourseInfoPanel, UUID> {
    Optional<CourseInfoPanel> findByCourseIdAndAcademicSessionAndIsDeletedFalse(UUID courseId, String academicSession);
    Optional<CourseInfoPanel> findFirstByCourseIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID courseId);
    List<CourseInfoPanel> findByCourseIdAndIsDeletedFalseOrderByAcademicSessionDesc(UUID courseId);
    List<CourseInfoPanel> findAllByIsDeletedFalseOrderByCreatedAtDesc();
    boolean existsByCourseIdAndAcademicSessionAndIsDeletedFalse(UUID courseId, String academicSession);
}
