package com.authvault.repository;

import com.authvault.entity.VaultItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultItemJpaRepository extends JpaRepository<VaultItem, Long> {}
