package com.vaultchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vaultchain.model.UserRecord;

public final class AccountResponse {
    private AccountResponse() {}

    public record User(long id, String fullName, String email, String role, String status,
                       String createdAt, String updatedAt) {
        public static User from(UserRecord user) {
            return new User(user.id(), user.fullName(), user.email(), user.role(), user.status(), user.createdAt(), user.updatedAt());
        }
    }

    public record MeUser(long id, @JsonProperty("full_name") String fullName, String email,
                         String role, String status, @JsonProperty("created_at") String createdAt) {
        public static MeUser from(UserRecord user) {
            return new MeUser(user.id(), user.fullName(), user.email(), user.role(), user.status(), user.createdAt());
        }
    }

    public record SignedIn(boolean success, String message, String token, User user) {}
    public record Me(boolean success, MeUser user) {}
    public record Profile(boolean success, String message, User user) {}
    public record Message(boolean success, String message) {}
}
