package com.authvault.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ImageComparisonPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(ImageComparisonProperties.class);

    @Test
    void loadsImageComparisonServiceConnectionConfiguration() {
        contextRunner
                .withPropertyValues(
                        "authvault.image-comparison.enabled=true",
                        "authvault.image-comparison.base-url=http://comparison-internal:9000",
                        "authvault.image-comparison.connect-timeout=3s",
                        "authvault.image-comparison.read-timeout=12s")
                .run(context -> {
                    ImageComparisonProperties properties =
                            context.getBean(ImageComparisonProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getBaseUrl())
                            .isEqualTo(URI.create("http://comparison-internal:9000"));
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(12));
                });
    }
}
