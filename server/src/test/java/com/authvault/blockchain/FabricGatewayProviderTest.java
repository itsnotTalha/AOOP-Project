package com.authvault.blockchain;

import com.authvault.config.FabricGatewayProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FabricGatewayProviderTest {

    @Test
    void enabledProviderFailsClearlyWhenRequiredMaterialIsNotConfigured() {
        FabricGatewayProperties properties = new FabricGatewayProperties();
        properties.setEnabled(true);

        assertThatThrownBy(() -> new FabricGatewayProvider(properties).connect())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "AUTHVAULT_FABRIC_TLS_CERT_PATH is required when blockchain is enabled");
    }
}
