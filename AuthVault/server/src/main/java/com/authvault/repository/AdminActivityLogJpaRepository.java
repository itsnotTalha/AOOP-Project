package com.authvault.repository;

import com.authvault.entity.AdminActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActivityLogJpaRepository extends JpaRepository<AdminActivityLog, Long> {}
