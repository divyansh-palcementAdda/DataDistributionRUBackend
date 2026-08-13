package com.app.datadistribution.repository;

import com.app.datadistribution.entity.DashboardCard;
import com.app.datadistribution.entity.Role;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DashboardCardRepository extends JpaRepository<DashboardCard, UUID>, JpaSpecificationExecutor<DashboardCard> {
    Optional<DashboardCard> findByCode(String code);
    Optional<DashboardCard> findByCodeIgnoreCase(String code);
    List<DashboardCard> findAllByActiveTrueOrderByDisplayOrderAsc();
    List<DashboardCard> findDistinctByAllowedRolesInAndActiveTrueOrderByDisplayOrderAsc(Set<Role> roles);
}
