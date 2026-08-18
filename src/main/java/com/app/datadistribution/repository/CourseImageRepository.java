package com.app.datadistribution.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.entity.CourseImage;

@Repository
public interface CourseImageRepository extends JpaRepository<CourseImage, UUID> {
    List<CourseImage> findByCourseIdAndIsDeletedFalseOrderByDisplayOrderAsc(UUID courseId);
    List<CourseImage> findByCourseIdAndActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc(UUID courseId);
    Optional<CourseImage> findByIdAndCourseIdAndIsDeletedFalse(UUID id, UUID courseId);
}
