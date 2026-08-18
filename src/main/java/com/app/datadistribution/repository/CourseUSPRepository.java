package com.app.datadistribution.repository;

import com.app.datadistribution.entity.CourseUSP;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseUSPRepository extends JpaRepository<CourseUSP, UUID> {
    List<CourseUSP> findByCourseIdAndIsDeletedFalseOrderByDisplayOrderAsc(UUID courseId);
    List<CourseUSP> findByCourseIdAndActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc(UUID courseId);
    Optional<CourseUSP> findByIdAndCourseIdAndIsDeletedFalse(UUID id, UUID courseId);
}
