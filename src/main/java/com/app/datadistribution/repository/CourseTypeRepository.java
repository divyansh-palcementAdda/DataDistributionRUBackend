package com.app.datadistribution.repository;

import com.app.datadistribution.entity.CourseType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseTypeRepository extends JpaRepository<CourseType, UUID>, JpaSpecificationExecutor<CourseType> {
    Optional<CourseType> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
