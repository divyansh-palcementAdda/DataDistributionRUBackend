package com.app.datadistribution.notification.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.notification.entity.UserNotificationPreference;

@Repository
public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, UUID> {

    Optional<UserNotificationPreference> findByUserId(UUID userId);
}

