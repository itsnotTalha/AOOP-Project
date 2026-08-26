package com.authvault.blockchain;

import com.authvault.config.FabricGatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledOriginalRegistryClientTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    FabricGatewayProperties.class,
                    DisabledOriginalRegistryClient.class);

    @Test
    void disabledConfigurationStartsWithoutFabricConnectionMaterial() {
        contextRunner
                .withPropertyValues("authvault.blockchain.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    OriginalRegistryClient client =
                            context.getBean(OriginalRegistryClient.class);
                    assertThat(client.isEnabled()).isFalse();
                    assertThatThrownBy(() -> client.getOriginal("ignored"))
                            .isInstanceOfSatisfying(
                                    OriginalRegistryClientException.class,
                                    exception -> assertThat(exception.getReason())
                                            .isEqualTo(
                                                    OriginalRegistryClientException.Reason.DISABLED));
                });
    }
}
