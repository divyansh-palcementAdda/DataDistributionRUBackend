package com.app.datadistribution.repository;

import com.app.datadistribution.entity.Course;
import com.app.datadistribution.enums.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {
    Optional<Course> findByCourseCode(String courseCode);
    Optional<Course> findByCourseCodeIgnoreCase(String courseCode);
    Optional<Course> findByCourseCodeIgnoreCaseAndIsDeletedFalse(String courseCode);
    Optional<Course> findByCourseNameIgnoreCase(String courseName);
    Optional<Course> findByCourseNameIgnoreCaseAndIsDeletedFalse(String courseName);
    List<Course> findAllByIsDeletedFalse();

    boolean existsByCourseNameIgnoreCase(String courseName);
    boolean existsByCourseNameIgnoreCaseAndIdNot(String courseName, UUID id);
    boolean existsByCourseCodeIgnoreCase(String courseCode);
    boolean existsByCourseCodeIgnoreCaseAndIdNot(String courseCode, UUID id);

    List<Course> findByStatusAndIsDeletedFalseOrderByCourseNameAsc(Status status);

    @Query("SELECT c FROM Course c JOIN c.programs p WHERE p.id = :programId AND c.isDeleted = false AND c.status = :status ORDER BY c.courseName ASC")
    List<Course> findActiveCoursesByProgramId(@Param("programId") UUID programId, @Param("status") Status status);

    default List<Course> findActiveCoursesByProgramId(UUID programId) {
        return findActiveCoursesByProgramId(programId, Status.ACTIVE);
    }

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"programs", "courseType"})
    @Query("SELECT DISTINCT c FROM Course c JOIN c.programs p WHERE p.id IN :programIds AND c.isDeleted = false AND c.status = :status ORDER BY c.courseName ASC")
    List<Course> findActiveCoursesByProgramIds(@Param("programIds") java.util.Collection<UUID> programIds, @Param("status") Status status);

    default List<Course> findActiveCoursesByProgramIds(java.util.Collection<UUID> programIds) {
        return findActiveCoursesByProgramIds(programIds, Status.ACTIVE);
    }

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"programs", "courseType"})
    @Query("SELECT c FROM Course c WHERE c.isDeleted = false AND c.status = :status ORDER BY c.courseName ASC")
    List<Course> findActiveCoursesWithProgramsAndType(@Param("status") Status status);

    default List<Course> findActiveCoursesWithProgramsAndType() {
        return findActiveCoursesWithProgramsAndType(Status.ACTIVE);
    }

    @Query("SELECT c FROM Course c JOIN c.programs p WHERE p.id = :programId AND c.courseType.id = :courseTypeId AND c.isDeleted = false AND c.status = :status ORDER BY c.courseName ASC")
    List<Course> findActiveCoursesByProgramIdAndCourseTypeId(@Param("programId") UUID programId, @Param("courseTypeId") UUID courseTypeId, @Param("status") Status status);

    default List<Course> findActiveCoursesByProgramIdAndCourseTypeId(UUID programId, UUID courseTypeId) {
        return findActiveCoursesByProgramIdAndCourseTypeId(programId, courseTypeId, Status.ACTIVE);
    }
}

