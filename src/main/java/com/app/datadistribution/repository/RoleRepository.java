package com.app.datadistribution.repository;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.app.datadistribution.Model.Role;
import com.app.datadistribution.payload.RoleDTO;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    // Summary list (ADMIN UI)
    @Query("""
        SELECT new com.cms.app.payload.RoleDTO(
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
