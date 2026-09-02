package com.app.datadistribution.repository;

import com.app.datadistribution.entity.Grade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface GradeRepository extends JpaRepository<Grade, UUID>, JpaSpecificationExecutor<Grade>, GradeRepositoryCustom {
    Optional<Grade> findByNameIgnoreCase(String name);
    Optional<Grade> findByCodeIgnoreCase(String code);
    Optional<Grade> findByCode(String code);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
