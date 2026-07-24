package com.verivault.security.util;

import com.verivault.entity.User;
import com.verivault.exception.UnauthorizedException;
import com.verivault.security.user.CustomUserDetails;
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
