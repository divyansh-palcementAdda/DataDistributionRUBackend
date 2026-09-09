package com.app.datadistribution.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.entity.Program;
import com.app.datadistribution.enums.Status;

@Repository
public interface ProgramRepository extends JpaRepository<Program, UUID>, JpaSpecificationExecutor<Program> {

    @Override
    @EntityGraph(attributePaths = {"courses"})
    Page<Program> findAll(@Nullable Specification<Program> spec, Pageable pageable);

    Optional<Program> findByNameIgnoreCase(String name);

    Optional<Program> findByNameIgnoreCaseAndIsDeletedFalse(String name);

    Optional<Program> findByCodeIgnoreCase(String code);

    Optional<Program> findByCodeIgnoreCaseAndIsDeletedFalse(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    List<Program> findByStatusAndIsDeletedFalseOrderByNameAsc(Status status);

    List<Program> findAllByStatusAndIsDeletedFalseOrderByNameAsc(Status status);

    List<Program> findByIsDeletedFalseOrderByNameAsc();

    @Query("SELECT p FROM Program p JOIN p.courses c WHERE c.id = :courseId AND p.isDeleted = false AND p.status = 'ACTIVE'")
    List<Program> findActiveProgramsByCourseId(@Param("courseId") UUID courseId);

    @Query("SELECT COUNT(c) > 0 FROM Program p JOIN p.courses c WHERE p.id = :programId AND c.id = :courseId AND p.isDeleted = false AND c.isDeleted = false")
    boolean isCourseMappedToProgram(@Param("programId") UUID programId, @Param("courseId") UUID courseId);
}
