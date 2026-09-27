package com.vaultchain.model;

/** Internal database row; controllers return account DTOs instead. */
public record UserRecord(long id, String fullName, String email, String passwordHash,
                         String role, String status, String createdAt, String updatedAt) {}
