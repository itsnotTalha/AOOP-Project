package com.authvault.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "authvault.image-comparison")
@Getter
@Setter
public class ImageComparisonProperties {

    private boolean enabled = false;
    private URI baseUrl = URI.create("http://localhost:8001");
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(10);
}
