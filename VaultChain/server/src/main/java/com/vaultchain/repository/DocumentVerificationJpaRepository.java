package com.vaultchain.repository;

import com.vaultchain.entity.DocumentVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVerificationJpaRepository extends JpaRepository<DocumentVerification, Long> {
    java.util.List<DocumentVerification> findByDocumentIdAndUserIdOrderByCreatedAtDescIdDesc(Long documentId, Long userId);
}
