package com.app.datadistribution.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.datadistribution.entity.UserLoginSession;
import com.app.datadistribution.enums.SessionStatus;

@Repository
public interface UserLoginSessionRepository extends JpaRepository<UserLoginSession, UUID>, JpaSpecificationExecutor<UserLoginSession> {

    @Query("SELECT uls FROM UserLoginSession uls WHERE uls.user.id = :userId AND uls.sessionStatus IN (:statuses) ORDER BY uls.loginAt DESC")
    List<UserLoginSession> findActiveSessionsForUser(@Param("userId") UUID userId, @Param("statuses") List<SessionStatus> statuses);

    @Query("SELECT uls FROM UserLoginSession uls WHERE uls.user.id = :userId AND uls.loginAt >= :startOfDay AND uls.loginAt <= :endOfDay AND uls.isDeleted = false ORDER BY uls.loginAt ASC")
    List<UserLoginSession> findSessionsForUserOnDate(@Param("userId") UUID userId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT uls FROM UserLoginSession uls WHERE uls.user.id = :userId AND uls.isDeleted = false ORDER BY uls.loginAt DESC")
    List<UserLoginSession> findLatestSessionsForUser(@Param("userId") UUID userId);

    @Query("SELECT uls FROM UserLoginSession uls WHERE uls.user.id = :userId AND uls.sessionStatus = 'ACTIVE' AND uls.isDeleted = false ORDER BY uls.loginAt DESC")
    Optional<UserLoginSession> findFirstActiveSessionByUserId(@Param("userId") UUID userId);
}
