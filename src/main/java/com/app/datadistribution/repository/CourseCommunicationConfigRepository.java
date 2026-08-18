package com.app.datadistribution.repository;

import com.app.datadistribution.entity.CourseCommunicationConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseCommunicationConfigRepository extends JpaRepository<CourseCommunicationConfig, UUID> {
    Optional<CourseCommunicationConfig> findByCourseIdAndIsDeletedFalse(UUID courseId);
}
