package com.app.datadistribution.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.app.datadistribution.entity.ActivityLog;
import com.app.datadistribution.enums.ActivityType;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    @Query("""
        SELECT a FROM ActivityLog a
        WHERE a.activityType <> :type
        ORDER BY a.createdAt DESC
    """)
    List<ActivityLog> findRecentExcludingType(@Param("type") ActivityType type, Pageable pageable);

    @Query("""
        SELECT a FROM ActivityLog a
        WHERE a.performedBy = :value 
        ORDER BY a.createdAt DESC
    """)
    List<ActivityLog> findByUser(@Param("value") String value, Pageable pageable);
    
    List<ActivityLog> findByActivityTypeOrderByCreatedAtDesc(ActivityType type, Pageable pageable);
    
    @Modifying
    @Query("""
        DELETE FROM ActivityLog a
        WHERE a.createdAt < :cutoffDate
    """)
    int deleteOldActivities(@Param("cutoffDate") LocalDateTime cutoffDate);
}