package com.vaultchain.repository;

import com.vaultchain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, Long> {
    java.util.Optional<User> findByEmail(String email);
}
