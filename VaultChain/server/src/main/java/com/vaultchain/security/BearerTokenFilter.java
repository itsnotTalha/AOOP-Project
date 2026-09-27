package com.vaultchain.security;

import com.vaultchain.exception.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerTokenFilter extends OncePerRequestFilter {
    private final TokenAuthentication authentication;
    private final SecurityErrors errors;
    private final RequestMatcher protectedRoutes;

    public BearerTokenFilter(TokenAuthentication authentication, SecurityErrors errors, RequestMatcher protectedRoutes) {
        this.authentication = authentication;
        this.errors = errors;
        this.protectedRoutes = protectedRoutes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !protectedRoutes.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            CurrentUser user = authentication.authenticate(request.getHeader("Authorization"));
            var authority = new SimpleGrantedAuthority(user.role().toUpperCase(Locale.ROOT));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of(authority)));
            SecurityContextHolder.setContext(context);
        } catch (ApiException failure) {
            errors.write(response, failure.getStatus(), failure.getMessage());
            return;
        } catch (RuntimeException failure) {
            errors.write(response, 500, failure.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }
}
