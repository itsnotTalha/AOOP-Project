package com.vaultchain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration(proxyBeanMethods = false)
public class WebConfiguration {
    @Bean
    CorsFilter corsFilter() {
        // Match Express cors(): all origins, no credential cookies, reflected request headers.
        CorsConfiguration cors = new CorsConfiguration();
        cors.addAllowedOrigin("*");
        cors.addAllowedHeader("*");
        cors.setAllowedMethods(java.util.List.of("GET", "HEAD", "PUT", "PATCH", "POST", "DELETE"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsFilter(source);
    }
}
