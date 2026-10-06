package com.app.datadistribution.repository;

import com.app.datadistribution.entity.Stream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface StreamRepository extends JpaRepository<Stream, UUID>, JpaSpecificationExecutor<Stream>, StreamRepositoryCustom {
    Optional<Stream> findByNameIgnoreCase(String name);
    Optional<Stream> findByNameIgnoreCaseAndIsDeletedFalse(String name);
    Optional<Stream> findByCodeIgnoreCase(String code);
    Optional<Stream> findByCodeIgnoreCaseAndIsDeletedFalse(String code);
    Optional<Stream> findByCode(String code);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
    List<Stream> findAllByIsDeletedFalseAndActiveTrue();
    List<Stream> findAllByIsDeletedFalse();
}
