package com.app.datadistribution.repository;

import com.app.datadistribution.entity.CourseTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseTemplateRepository extends JpaRepository<CourseTemplate, UUID> {
    List<CourseTemplate> findByCourseIdAndIsDeletedFalse(UUID courseId);
    List<CourseTemplate> findByCourseIdInAndIsDeletedFalse(List<UUID> courseIds);
    List<CourseTemplate> findByIsDeletedFalse();
}
