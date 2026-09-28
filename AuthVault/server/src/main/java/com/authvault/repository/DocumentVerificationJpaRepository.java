package com.authvault.repository;

import com.authvault.entity.DocumentVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVerificationJpaRepository extends JpaRepository<DocumentVerification, Long> {
    java.util.List<DocumentVerification> findByDocumentIdAndUserIdOrderByCreatedAtDescIdDesc(Long documentId, Long userId);
}
