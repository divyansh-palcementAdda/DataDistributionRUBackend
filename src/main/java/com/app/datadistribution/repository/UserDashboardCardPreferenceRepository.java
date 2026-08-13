package com.app.datadistribution.repository;

import com.app.datadistribution.entity.UserDashboardCardPreference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDashboardCardPreferenceRepository extends JpaRepository<UserDashboardCardPreference, UUID> {
    List<UserDashboardCardPreference> findByUserId(UUID userId);
    List<UserDashboardCardPreference> findByUserIdOrderByDisplayOrderAsc(UUID userId);
    Optional<UserDashboardCardPreference> findByUserIdAndDashboardCardId(UUID userId, UUID cardId);
    void deleteByUserId(UUID userId);
}
