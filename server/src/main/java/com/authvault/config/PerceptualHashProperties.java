package com.authvault.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "authvault.verification.phash")
@Validated
@Getter
@Setter
public class PerceptualHashProperties {

    private boolean enabled = true;

    @Min(0)
    @Max(64)
    private int reviewThreshold = 6;

    @Min(0)
    @Max(64)
    private int possibleMatchThreshold = 14;

    @Positive
    private int maxCandidates = 5;

    @AssertTrue(message = "review-threshold must be less than or equal to possible-match-threshold")
    public boolean isThresholdOrderValid() {
        return reviewThreshold <= possibleMatchThreshold;
    }
}
