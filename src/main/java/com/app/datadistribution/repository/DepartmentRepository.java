package com.app.datadistribution.repository;

import com.app.datadistribution.entity.Department;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID>, JpaSpecificationExecutor<Department> {
    Optional<Department> findByNameIgnoreCaseAndIsDeletedFalse(String name);
    Optional<Department> findByCodeIgnoreCaseAndIsDeletedFalse(String code);
    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);
    boolean existsByCodeIgnoreCaseAndIsDeletedFalse(String code);
    boolean existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(String name, UUID id);
    boolean existsByCodeIgnoreCaseAndIsDeletedFalseAndIdNot(String code, UUID id);
    List<Department> findByActiveTrueAndIsDeletedFalse();
}
