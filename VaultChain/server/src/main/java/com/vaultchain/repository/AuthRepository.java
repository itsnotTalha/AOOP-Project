package com.vaultchain.repository;

import com.vaultchain.entity.User;
import com.vaultchain.entity.Wallet;
import com.vaultchain.model.UserRecord;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuthRepository {
    private static final DateTimeFormatter SQLITE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final UserJpaRepository users;
    private final WalletJpaRepository wallets;
    private final jakarta.persistence.EntityManager entityManager;

    public AuthRepository(UserJpaRepository users, WalletJpaRepository wallets, jakarta.persistence.EntityManager entityManager) {
        this.users = users;
        this.wallets = wallets;
        this.entityManager = entityManager;
    }

    private UserRecord record(User user) {
        return new UserRecord(user.getId(), user.getFullName(), user.getEmail(), user.getPasswordHash(),
                user.getRole(), user.getStatus(), user.getCreatedAt(), user.getUpdatedAt());
    }

    public Optional<UserRecord> findById(Long id) {
        return id == null ? Optional.empty() : users.findById(id).map(this::record);
    }

    public Optional<UserRecord> findByEmail(String email) {
        return users.findByEmail(email).map(this::record);
    }

    @Transactional
    public UserRecord createWithWallet(String name, String email, String passwordHash) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole("USER");
        user.setStatus("active");
        user = users.saveAndFlush(user);
        Wallet wallet = new Wallet();
        wallet.setUserId(user.getId());
        wallet.setBalance(0.0);
        wallets.saveAndFlush(wallet);
        entityManager.refresh(user);
        return record(user);
    }

    @Transactional
    public UserRecord updateProfile(long id, String name, String email) {
        User user = users.findById(id).orElseThrow();
        user.setFullName(name);
        user.setEmail(email);
        user.setUpdatedAt(SQLITE_TIME.format(LocalDateTime.now(ZoneOffset.UTC)));
        return record(users.saveAndFlush(user));
    }

    @Transactional
    public void updatePassword(long id, String passwordHash) {
        User user = users.findById(id).orElseThrow();
        user.setPasswordHash(passwordHash);
        user.setUpdatedAt(SQLITE_TIME.format(LocalDateTime.now(ZoneOffset.UTC)));
        users.saveAndFlush(user);
    }
}
