package com.authvault.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FabricGatewayPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(FabricGatewayProperties.class);

    @Test
    void defaultsToDisabledWithoutRequiringConnectionMaterial() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            FabricGatewayProperties properties = context.getBean(FabricGatewayProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getMspId()).isEqualTo("CreatorsOrgMSP");
            assertThat(properties.getChannel()).isEqualTo("vaultchain-channel");
            assertThat(properties.getChaincode()).isEqualTo("registered-original-registry");
            assertThat(properties.getPeerEndpoint()).isEqualTo("localhost:7051");
            assertThat(properties.getPeerHostOverride())
                    .isEqualTo("peer0.creators.vaultchain.local");
        });
    }

    @Test
    void bindsAllEnvironmentBackedSettings() {
        contextRunner.withPropertyValues(
                "authvault.blockchain.enabled=true",
                "authvault.blockchain.msp-id=TestMSP",
                "authvault.blockchain.channel=test-channel",
                "authvault.blockchain.chaincode=test-registry",
                "authvault.blockchain.peer-endpoint=peer.example:7443",
                "authvault.blockchain.peer-host-override=peer.example",
                "authvault.blockchain.tls-cert-path=certs/tls.pem",
                "authvault.blockchain.cert-path=certs/user.pem",
                "authvault.blockchain.private-key-path=certs/key.pem")
                .run(context -> {
                    FabricGatewayProperties properties =
                            context.getBean(FabricGatewayProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getMspId()).isEqualTo("TestMSP");
                    assertThat(properties.getChannel()).isEqualTo("test-channel");
                    assertThat(properties.getChaincode()).isEqualTo("test-registry");
                    assertThat(properties.getPeerEndpoint()).isEqualTo("peer.example:7443");
                    assertThat(properties.getPeerHostOverride()).isEqualTo("peer.example");
                    assertThat(properties.getTlsCertPath()).isEqualTo("certs/tls.pem");
                    assertThat(properties.getCertPath()).isEqualTo("certs/user.pem");
                    assertThat(properties.getPrivateKeyPath()).isEqualTo("certs/key.pem");
                });
    }
}
