package com.app.datadistribution.repository;

import com.app.datadistribution.entity.Lead;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {
    Optional<Lead> findByLeadCode(String leadCode);
    boolean existsByLeadCode(String leadCode);

    @Query("SELECT l.currentStatus, COUNT(l) FROM Lead l WHERE l.isDeleted = false GROUP BY l.currentStatus")
    List<Object[]> countByStatus();

    @Query("SELECT l.source, COUNT(l) FROM Lead l WHERE l.isDeleted = false GROUP BY l.source")
    List<Object[]> countBySource();
}
