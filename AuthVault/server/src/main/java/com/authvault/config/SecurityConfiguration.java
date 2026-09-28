package com.authvault.config;

import com.authvault.security.BearerTokenFilter;
import com.authvault.security.SecurityErrors;
import com.authvault.security.TokenAuthentication;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.CorsFilter;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<CorsFilter> corsRegistration(CorsFilter cors) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(cors);
        registration.setEnabled(false); // Registered once, inside the security chain before authentication.
        return registration;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, TokenAuthentication authentication,
                                 SecurityErrors errors, CorsFilter cors) throws Exception {
        // Only migrated endpoints are protected. Unimplemented/unknown routes still reach the 404 handler.
        RequestMatcher protectedRoutes = request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String method = request.getMethod();
            if (path.equals("/api/admin") || path.startsWith("/api/admin/")) return true;
            if (path.equals("/api/documents") || path.startsWith("/api/documents/")) return true;
            if (path.equals("/api/wallet") || path.startsWith("/api/wallet/")) return true;
            if (path.equals("/api/dashboard/summary")) return true;
            if (path.equals("/api/marketplace") || path.startsWith("/api/marketplace/")) return true;
            if (path.equals("/api/verifications") || path.startsWith("/api/verifications/")) return true;
            if (path.equals("/api/assets") || path.startsWith("/api/assets/")) return true;
            if (path.equals("/api/vaults") || path.startsWith("/api/vaults/")) return true;
            return switch (path) {
                case "/api/auth/me" -> method.equals("GET") || method.equals("HEAD");
                case "/api/auth/profile", "/api/auth/password" -> method.equals("PATCH");
                case "/api/auth/logout" -> method.equals("POST");
                default -> false;
            };
        };
        http.csrf(config -> config.disable())
                .formLogin(config -> config.disable()).httpBasic(config -> config.disable())
                .logout(config -> config.disable()).requestCache(config -> config.disable())
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Preserve controller cache policies; Spring defaults must not replace them.
                .headers(config -> config.cacheControl(cache -> cache.disable()))
                .authorizeHttpRequests(config -> config.requestMatchers(protectedRoutes).authenticated().anyRequest().permitAll())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, failure) -> errors.write(response, 401, "Unauthorized"))
                        .accessDeniedHandler((request, response, failure) -> errors.write(response, 403, "You do not have permission to perform this action")))
                .addFilterBefore(cors, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new BearerTokenFilter(authentication, errors, protectedRoutes), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
