package com.vaultchain.repository;

import com.vaultchain.entity.AdminActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActivityLogJpaRepository extends JpaRepository<AdminActivityLog, Long> {}
