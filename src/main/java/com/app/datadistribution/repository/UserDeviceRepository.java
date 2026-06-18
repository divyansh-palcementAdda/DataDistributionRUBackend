package com.app.datadistribution.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.app.datadistribution.entity.UserDevice;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {
    List<UserDevice> findByUserIdOrderByLastActivityAtDesc(UUID userId);
    List<UserDevice> findByUserIdAndIsActiveTrue(UUID userId);
    List<UserDevice> findByUserId(UUID userId);
    Optional<UserDevice> findByRefreshTokenId(UUID refreshTokenId);
    Optional<UserDevice> findByIdAndUserId(UUID id, UUID userId);
}
