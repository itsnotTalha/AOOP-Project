package com.authvault.blockchain;

import com.authvault.config.FabricGatewayProperties;
import com.authvault.config.UploadProperties;
import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.blockchain.BlockchainRegistrationResponse;
import com.authvault.dto.originality.OriginalityOutcome;
import com.authvault.entity.User;
import com.authvault.security.user.CustomUserDetails;
import com.authvault.service.RegisteredOriginalCandidateService;
import com.authvault.client.comparison.ImageComparisonClient;
import com.authvault.service.impl.LocalAssetStorageService;
import com.authvault.service.impl.OriginalityVerificationServiceImpl;
import com.authvault.service.impl.PerceptualHashServiceImpl;
import com.authvault.service.impl.Sha256ServiceImpl;
import com.authvault.validation.UploadFileValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@EnabledIfEnvironmentVariable(
        named = "AUTHVAULT_FABRIC_INTEGRATION_TEST_ENABLED",
        matches = "(?i)true")
class FabricOriginalRegistryLiveIntegrationTest {

    @Test
    void registersQueriesAndVerifiesExactOriginalAgainstRunningFabricNetwork(
            @TempDir Path testDirectory) throws Exception {
        FabricGatewayProvider provider = new FabricGatewayProvider(propertiesFromEnvironment());
        try {
            provider.connect();
            FabricOriginalRegistryClient client = new FabricOriginalRegistryClient(
                    provider, new ObjectMapper().findAndRegisterModules());
            CanonicalSha256Hasher hasher =
                    new CanonicalSha256Hasher(new Sha256ServiceImpl());
            String assetId = UUID.randomUUID().toString();
            byte[] imageBytes = uniquePng(assetId);
            String creatorIdHash = hasher.hashUtf8("AUTHVAULT_FABRIC_LIVE_TEST_CREATOR:" + assetId);
            String sha256 = new Sha256ServiceImpl()
                    .calculate(new ByteArrayInputStream(imageBytes))
                    .hash();
            String evidenceHash = hasher.hashUtf8("AUTHVAULT_FABRIC_LIVE_TEST_EVIDENCE:" + assetId);

            BlockchainRegistrationResponse registration = client.registerOriginal(
                    new OriginalRegistryClient.RegistrationRequest(
                            assetId,
                            creatorIdHash,
                            sha256,
                            "IMAGE",
                            "VERIFIED",
                            evidenceHash));

            assertThat(registration.transactionId()).isNotBlank();
            assertThat(registration.asset().assetId()).isEqualTo(assetId);
            BlockchainOriginalResponse byId = client.getOriginal(assetId);
            assertThat(byId.sha256()).isEqualTo(sha256);
            assertThat(client.findBySha256(sha256)).contains(byId);
            assertThat(client.getAssetHistory(assetId))
                    .isNotEmpty()
                    .allSatisfy(entry -> assertThat(entry.transactionId()).isNotBlank());

            RegisteredOriginalCandidateService candidateService =
                    mock(RegisteredOriginalCandidateService.class);
            ImageComparisonClient imageComparisonClient = mock(ImageComparisonClient.class);
            UploadProperties uploadProperties = new UploadProperties();
            uploadProperties.setRootDirectory(testDirectory.resolve("uploads"));
            OriginalityVerificationServiceImpl originalityService =
                    new OriginalityVerificationServiceImpl(
                            new UploadFileValidator(uploadProperties),
                            new LocalAssetStorageService(uploadProperties),
                            new Sha256ServiceImpl(),
                            new PerceptualHashServiceImpl(),
                            client,
                            candidateService,
                            imageComparisonClient);
            authenticateTestUser();

            var originality = originalityService.verify(new MockMultipartFile(
                    "image", "exact.png", "image/png", imageBytes));

            assertThat(originality.outcome())
                    .isEqualTo(OriginalityOutcome.EXACT_REGISTERED_ORIGINAL);
            assertThat(originality.registeredOriginal().assetId()).isEqualTo(assetId);
            assertThat(originality.blockchainLookup().exactMatch()).isTrue();
            verify(candidateService, never()).findPlausibleCandidates(any());
            verify(imageComparisonClient, never()).compareImages(any(), any(), any(), any());
        } finally {
            SecurityContextHolder.clearContext();
            provider.close();
        }
    }

    private byte[] uniquePng(String seed) throws Exception {
        byte[] seedBytes = seed.getBytes(StandardCharsets.UTF_8);
        BufferedImage image = new BufferedImage(
                seedBytes.length, 1, BufferedImage.TYPE_INT_RGB);
        for (int index = 0; index < seedBytes.length; index++) {
            int value = Byte.toUnsignedInt(seedBytes[index]);
            image.setRGB(index, 0, (value << 16) | (value << 8) | value);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IllegalStateException("PNG writer is unavailable");
        }
        return output.toByteArray();
    }

    private void authenticateTestUser() {
        User user = new User();
        user.setUuid(UUID.randomUUID().toString());
        user.setUsername("fabric-live-originality");
        user.setRole(User.Role.USER);
        user.setStatus(User.Status.ACTIVE);
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()));
    }

    private FabricGatewayProperties propertiesFromEnvironment() {
        FabricGatewayProperties properties = new FabricGatewayProperties();
        properties.setEnabled(true);
        properties.setMspId(environmentOrDefault(
                "AUTHVAULT_FABRIC_MSP_ID", properties.getMspId()));
        properties.setChannel(environmentOrDefault(
                "AUTHVAULT_FABRIC_CHANNEL", properties.getChannel()));
        properties.setChaincode(environmentOrDefault(
                "AUTHVAULT_FABRIC_CHAINCODE", properties.getChaincode()));
        properties.setPeerEndpoint(environmentOrDefault(
                "AUTHVAULT_FABRIC_PEER_ENDPOINT", properties.getPeerEndpoint()));
        properties.setPeerHostOverride(environmentOrDefault(
                "AUTHVAULT_FABRIC_PEER_HOST_OVERRIDE", properties.getPeerHostOverride()));
        properties.setTlsCertPath(System.getenv("AUTHVAULT_FABRIC_TLS_CERT_PATH"));
        properties.setCertPath(System.getenv("AUTHVAULT_FABRIC_CERT_PATH"));
        properties.setPrivateKeyPath(System.getenv("AUTHVAULT_FABRIC_PRIVATE_KEY_PATH"));
        return properties;
    }

    private String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
