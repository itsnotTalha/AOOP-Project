package com.vaultchain.repository;

import com.vaultchain.entity.Vault;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultJpaRepository extends JpaRepository<Vault, Long> {
    java.util.Optional<Vault> findByPublicReferenceAndUserId(String reference, Long userId);
    java.util.List<Vault> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId);
    long countByUserId(Long userId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from Vault v where v.id = :id")
    void deleteVaultById(@org.springframework.data.repository.query.Param("id") Long id);
}
