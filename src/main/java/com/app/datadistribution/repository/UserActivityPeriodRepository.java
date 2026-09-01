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

import com.app.datadistribution.entity.UserActivityPeriod;
import com.app.datadistribution.enums.ActivityPeriodType;

@Repository
public interface UserActivityPeriodRepository extends JpaRepository<UserActivityPeriod, UUID>, JpaSpecificationExecutor<UserActivityPeriod> {

    @Query("SELECT uap FROM UserActivityPeriod uap WHERE uap.session.id = :sessionId AND uap.endedAt IS NULL ORDER BY uap.startedAt DESC")
    Optional<UserActivityPeriod> findOpenPeriodForSession(@Param("sessionId") UUID sessionId);

    @Query("SELECT uap FROM UserActivityPeriod uap WHERE uap.user.id = :userId AND uap.startedAt >= :startOfDay AND uap.startedAt <= :endOfDay AND uap.isDeleted = false ORDER BY uap.startedAt ASC")
    List<UserActivityPeriod> findPeriodsForUserOnDate(@Param("userId") UUID userId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT uap FROM UserActivityPeriod uap WHERE uap.user.id = :userId AND uap.periodType = :periodType AND uap.startedAt >= :startOfDay AND uap.startedAt <= :endOfDay AND uap.isDeleted = false ORDER BY uap.startedAt ASC")
    List<UserActivityPeriod> findPeriodsForUserOnDateByType(@Param("userId") UUID userId, @Param("periodType") ActivityPeriodType periodType, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT uap FROM UserActivityPeriod uap WHERE uap.session.id = :sessionId AND uap.isDeleted = false ORDER BY uap.startedAt ASC")
    List<UserActivityPeriod> findPeriodsBySessionId(@Param("sessionId") UUID sessionId);
}
