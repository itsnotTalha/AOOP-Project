package com.authvault.security;

import com.authvault.exception.ApiException;
import java.util.Arrays;

public enum Role {
    SUPER_ADMIN, MODERATOR, FINANCE_ADMIN, VERIFICATION_ADMIN, USER;

    public static void requireAny(CurrentUser user, Role... allowed) {
        if (user == null || Arrays.stream(allowed).noneMatch(role -> role.name().equalsIgnoreCase(user.role()))) {
            throw new ApiException(403, "You do not have permission to perform this action");
        }
    }
}
