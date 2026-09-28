package com.authvault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.DefaultCorsProcessor;
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
        CorsFilter filter = new CorsFilter(source);
        DefaultCorsProcessor processor = new DefaultCorsProcessor();
        filter.setCorsProcessor((configuration, request, response) -> {
            boolean allowed = processor.processRequest(configuration, request, response);
            if (allowed && CorsUtils.isPreFlightRequest(request)) response.setStatus(204);
            return allowed;
        });
        return filter;
    }
}
