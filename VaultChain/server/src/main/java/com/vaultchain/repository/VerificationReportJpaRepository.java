package com.vaultchain.repository;

import com.vaultchain.entity.VerificationReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationReportJpaRepository extends JpaRepository<VerificationReport, Long> {
 java.util.List<VerificationReport> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
