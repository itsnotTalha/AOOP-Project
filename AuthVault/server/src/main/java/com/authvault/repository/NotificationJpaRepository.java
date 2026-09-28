package com.authvault.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.authvault.entity.Notification;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = 1 WHERE n.userId = :userId")
    void markAllReadByUserId(@Param("userId") Long userId);
}
