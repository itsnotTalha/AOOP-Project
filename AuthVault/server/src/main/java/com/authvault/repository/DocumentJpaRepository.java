package com.authvault.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.authvault.entity.Document;

public interface DocumentJpaRepository extends JpaRepository<Document, Long> {
    java.util.Optional<Document> findByIdAndOwnerId(Long id, Long ownerId);
    java.util.List<Document> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);
    java.util.Optional<Document> findByAssetId(Long assetId);
    java.util.Optional<Document> findByStoredName(String storedName);
    java.util.List<Document> findBySha256Hash(String sha256Hash);
}
