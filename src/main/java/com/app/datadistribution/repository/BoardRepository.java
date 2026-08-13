package com.app.datadistribution.repository;

import com.app.datadistribution.entity.Board;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardRepository extends JpaRepository<Board, UUID>, JpaSpecificationExecutor<Board> {
    Optional<Board> findByNameIgnoreCase(String name);
    Optional<Board> findByCodeIgnoreCase(String code);
    Optional<Board> findByCode(String code);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
