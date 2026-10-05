package com.authvault.repository;

import com.authvault.entity.OcrResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcrResultJpaRepository extends JpaRepository<OcrResult, Long> {
    java.util.Optional<OcrResult> findByDocumentId(Long documentId);
}
