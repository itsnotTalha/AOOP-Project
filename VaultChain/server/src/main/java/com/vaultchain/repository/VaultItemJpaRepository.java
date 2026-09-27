package com.vaultchain.repository;

import com.vaultchain.entity.VaultItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultItemJpaRepository extends JpaRepository<VaultItem, Long> {}
