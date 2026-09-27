package com.vaultchain.repository;

import com.vaultchain.entity.FractionalOwnership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FractionalOwnershipJpaRepository extends JpaRepository<FractionalOwnership, Long> {}
