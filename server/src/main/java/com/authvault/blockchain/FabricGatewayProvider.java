package com.authvault.blockchain;

import com.authvault.config.FabricGatewayProperties;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Hash;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "authvault.blockchain", name = "enabled", havingValue = "true")
public class FabricGatewayProvider {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final FabricGatewayProperties properties;
    private ManagedChannel grpcChannel;
    private Gateway gateway;
    private Contract contract;

    public FabricGatewayProvider(FabricGatewayProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void connect() {
        validateText(properties.getMspId(), "AUTHVAULT_FABRIC_MSP_ID");
        validateText(properties.getChannel(), "AUTHVAULT_FABRIC_CHANNEL");
        validateText(properties.getChaincode(), "AUTHVAULT_FABRIC_CHAINCODE");
        validateText(properties.getPeerEndpoint(), "AUTHVAULT_FABRIC_PEER_ENDPOINT");
        validateText(properties.getPeerHostOverride(), "AUTHVAULT_FABRIC_PEER_HOST_OVERRIDE");

        Path tlsCertificatePath = requiredFile(
                properties.getTlsCertPath(), "AUTHVAULT_FABRIC_TLS_CERT_PATH");
        Path certificatePath = requiredFile(
                properties.getCertPath(), "AUTHVAULT_FABRIC_CERT_PATH");
        Path privateKeyPath = requiredFile(
                properties.getPrivateKeyPath(), "AUTHVAULT_FABRIC_PRIVATE_KEY_PATH");

        try (Reader certificateReader = Files.newBufferedReader(
                certificatePath, StandardCharsets.UTF_8);
             Reader privateKeyReader = Files.newBufferedReader(
                     privateKeyPath, StandardCharsets.UTF_8)) {
            X509Certificate certificate = Identities.readX509Certificate(certificateReader);
            PrivateKey privateKey = Identities.readPrivateKey(privateKeyReader);
            Identity identity = new X509Identity(properties.getMspId(), certificate);
            Signer signer = Signers.newPrivateKeySigner(privateKey);
            ChannelCredentials credentials = TlsChannelCredentials.newBuilder()
                    .trustManager(tlsCertificatePath.toFile())
                    .build();

            grpcChannel = Grpc.newChannelBuilder(properties.getPeerEndpoint(), credentials)
                    .overrideAuthority(properties.getPeerHostOverride())
                    .build();
            gateway = Gateway.newInstance()
                    .identity(identity)
                    .signer(signer)
                    .hash(Hash.SHA256)
                    .connection(grpcChannel)
                    .connect();
            contract = gateway
                    .getNetwork(properties.getChannel())
                    .getContract(properties.getChaincode());
        } catch (Exception exception) {
            close();
            throw new IllegalStateException(
                    "Fabric Gateway initialization failed; verify the configured identity and TLS material",
                    exception);
        }
    }

    public Contract getContract() {
        if (contract == null) {
            throw new IllegalStateException("Fabric Gateway is not initialized");
        }
        return contract;
    }

    @PreDestroy
    public void close() {
        Gateway currentGateway = gateway;
        ManagedChannel currentChannel = grpcChannel;
        gateway = null;
        contract = null;
        grpcChannel = null;
        try {
            if (currentGateway != null) {
                currentGateway.close();
            }
        } finally {
            shutdownChannel(currentChannel);
        }
    }

    private void shutdownChannel(ManagedChannel channel) {
        if (channel != null) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException exception) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private Path requiredFile(String configuredPath, String variableName) {
        validateText(configuredPath, variableName);
        Path path;
        try {
            path = Path.of(configuredPath).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(variableName + " must be a valid file path", exception);
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException(variableName + " must reference a readable regular file");
        }
        return path;
    }

    private void validateText(String value, String variableName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variableName + " is required when blockchain is enabled");
        }
    }
}
