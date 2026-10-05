package com.authvault.repository;

import com.authvault.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, Long> {
    java.util.Optional<User> findByEmail(String email);
}
