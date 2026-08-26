package com.authvault.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "authvault.blockchain")
@Getter
@Setter
public class FabricGatewayProperties {

    private boolean enabled;
    private String mspId = "CreatorsOrgMSP";
    private String channel = "vaultchain-channel";
    private String chaincode = "registered-original-registry";
    private String peerEndpoint = "localhost:7051";
    private String peerHostOverride = "peer0.creators.vaultchain.local";
    private String tlsCertPath;
    private String certPath;
    private String privateKeyPath;
}
