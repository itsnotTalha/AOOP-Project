package com.authvault.security.util;

import com.authvault.entity.User;
import com.authvault.exception.UnauthorizedException;
import com.authvault.security.user.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static User getCurrentUser() {
        Authentication authentication = getAuthentication();

        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUser();
        }

        throw new UnauthorizedException("No authenticated user found");
    }

    public static String getCurrentUsername() {
        return getCurrentUser().getUsername();
    }

    private static Authentication getAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Authentication is required");
        }

        return authentication;
    }
}
