package com.authvault.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiServicePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(AiServiceProperties.class);

    @Test
    void loadsDisabledInternalServiceConfiguration() {
        contextRunner
                .withPropertyValues(
                        "authvault.ai-service.enabled=false",
                        "authvault.ai-service.base-url=http://ai-internal:9000",
                        "authvault.ai-service.connect-timeout=3s",
                        "authvault.ai-service.read-timeout=12s",
                        "authvault.ai-service.analysis-read-timeout=75s")
                .run(context -> {
                    AiServiceProperties properties = context.getBean(AiServiceProperties.class);
                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getBaseUrl()).isEqualTo(URI.create("http://ai-internal:9000"));
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(12));
                    assertThat(properties.getAnalysisReadTimeout())
                            .isEqualTo(Duration.ofSeconds(75));
                });
    }
}
