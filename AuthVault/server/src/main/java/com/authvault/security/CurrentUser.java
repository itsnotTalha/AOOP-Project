package com.authvault.security;

import java.security.Principal;

public record CurrentUser(long id, String email, String role, String status, String tokenFingerprint) implements Principal {
    @Override public String getName() { return Long.toString(id); }
}
