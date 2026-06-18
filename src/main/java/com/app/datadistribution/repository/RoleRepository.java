package com.app.datadistribution.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.dto.user.RoleDTO;
import com.app.datadistribution.entity.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    @Query("""
        SELECT new com.app.datadistribution.dto.user.RoleDTO(
            r.id,
            r.name,
            COUNT(u)
        )
        FROM Role r
        LEFT JOIN r.users u
        GROUP BY r.id, r.name
    """)
    List<RoleDTO> fetchAllSummary();
}
