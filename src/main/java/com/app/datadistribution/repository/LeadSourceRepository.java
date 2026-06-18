package com.app.datadistribution.repository;

import com.app.datadistribution.entity.LeadSource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadSourceRepository extends JpaRepository<LeadSource, UUID>, JpaSpecificationExecutor<LeadSource> {
    Optional<LeadSource> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
