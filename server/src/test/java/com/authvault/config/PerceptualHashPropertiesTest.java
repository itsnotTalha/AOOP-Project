package com.authvault.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PerceptualHashPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(PerceptualHashProperties.class);

    @Test
    void loadsValidConfiguration() {
        contextRunner.withPropertyValues(
                        "authvault.verification.phash.enabled=true",
                        "authvault.verification.phash.review-threshold=7",
                        "authvault.verification.phash.possible-match-threshold=16",
                        "authvault.verification.phash.max-candidates=8",
                        "authvault.verification.phash.registry-scan-limit=120")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PerceptualHashProperties properties =
                            context.getBean(PerceptualHashProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getReviewThreshold()).isEqualTo(7);
                    assertThat(properties.getPossibleMatchThreshold()).isEqualTo(16);
                    assertThat(properties.getMaxCandidates()).isEqualTo(8);
                    assertThat(properties.getRegistryScanLimit()).isEqualTo(120);
                });
    }

    @Test
    void rejectsNegativeThreshold() {
        contextRunner.withPropertyValues(
                        "authvault.verification.phash.review-threshold=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsThresholdsInInvalidOrder() {
        contextRunner.withPropertyValues(
                        "authvault.verification.phash.review-threshold=20",
                        "authvault.verification.phash.possible-match-threshold=10")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveMaxCandidates() {
        contextRunner.withPropertyValues(
                        "authvault.verification.phash.max-candidates=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveRegistryScanLimit() {
        contextRunner.withPropertyValues(
                        "authvault.verification.phash.registry-scan-limit=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
