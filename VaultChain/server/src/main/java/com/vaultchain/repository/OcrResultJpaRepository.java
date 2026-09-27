package com.vaultchain.repository;

import com.vaultchain.entity.OcrResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcrResultJpaRepository extends JpaRepository<OcrResult, Long> {
    java.util.Optional<OcrResult> findByDocumentId(Long documentId);
}
