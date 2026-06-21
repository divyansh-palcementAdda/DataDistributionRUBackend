package com.app.datadistribution.repository;

import com.app.datadistribution.entity.Permission;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByName(String name);
    Optional<Permission> findByNameAndIsDeletedFalse(String name);
    Optional<Permission> findByIdAndIsDeletedFalse(UUID id);
    boolean existsByName(String name);
    boolean existsByNameAndIsDeletedFalse(String name);
    java.util.List<Permission> findAllByIsDeletedFalse();
}
