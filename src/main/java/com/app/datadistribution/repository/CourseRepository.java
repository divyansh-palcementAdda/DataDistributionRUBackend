package com.app.datadistribution.repository;

import com.app.datadistribution.entity.Course;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {
    Optional<Course> findByCourseCode(String courseCode);
    boolean existsByCourseNameIgnoreCase(String courseName);
    boolean existsByCourseNameIgnoreCaseAndIdNot(String courseName, UUID id);
    boolean existsByCourseCodeIgnoreCase(String courseCode);
    boolean existsByCourseCodeIgnoreCaseAndIdNot(String courseCode, UUID id);
}
