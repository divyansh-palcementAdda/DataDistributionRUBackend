package com.app.datadistribution.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.datadistribution.Model.UserDevice;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    List<UserDevice> findByUserIdOrderByLastActivityAtDesc(Long userId);
    List<UserDevice> findByUserIdAndIsActiveTrue(Long userId);
    Optional<UserDevice> findByRefreshTokenId(Long refreshTokenId);
    Optional<UserDevice> findByIdAndUserId(Long id, Long userId);
}
