package com.authvault.blockchain;

import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.blockchain.BlockchainRegistrationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.GatewayException;
import org.hyperledger.fabric.client.Proposal;
import org.hyperledger.fabric.client.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FabricOriginalRegistryClientTest {

    private static final String ASSET_ID = "11111111-1111-1111-1111-111111111111";
    private static final String CREATOR_HASH =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String EVIDENCE_HASH =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String ORIGINAL_JSON = """
            {"assetId":"11111111-1111-1111-1111-111111111111",\
            "creatorIdHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",\
            "currentOwnerIdHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",\
            "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",\
            "assetType":"IMAGE","verificationStatus":"VERIFIED",\
            "evidenceHash":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",\
            "registeredAt":"2026-08-15T05:22:33Z"}
            """;

    @Mock
    private FabricGatewayProvider gatewayProvider;
    @Mock
    private Contract contract;

    private FabricOriginalRegistryClient client;

    @BeforeEach
    void setUp() {
        when(gatewayProvider.getContract()).thenReturn(contract);
        client = new FabricOriginalRegistryClient(
                gatewayProvider, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void submitsRegisterOriginalAndReturnsRealGatewayTransactionId() throws Exception {
        Proposal.Builder builder = org.mockito.Mockito.mock(Proposal.Builder.class);
        Proposal proposal = org.mockito.Mockito.mock(Proposal.class);
        Transaction transaction = org.mockito.Mockito.mock(Transaction.class);
        when(contract.newProposal("RegisterOriginal")).thenReturn(builder);
        when(builder.addArguments(
                ASSET_ID, CREATOR_HASH, SHA256, "IMAGE", "VERIFIED", EVIDENCE_HASH))
                .thenReturn(builder);
        when(builder.build()).thenReturn(proposal);
        when(proposal.endorse()).thenReturn(transaction);
        when(transaction.submit()).thenReturn(ORIGINAL_JSON.getBytes(StandardCharsets.UTF_8));
        when(transaction.getTransactionId()).thenReturn("fabric-tx-123");

        BlockchainRegistrationResponse response = client.registerOriginal(
                new OriginalRegistryClient.RegistrationRequest(
                        ASSET_ID, CREATOR_HASH, SHA256, "IMAGE", "VERIFIED", EVIDENCE_HASH));

        assertThat(response.transactionId()).isEqualTo("fabric-tx-123");
        assertThat(response.asset().assetId()).isEqualTo(ASSET_ID);
        assertThat(response.asset().registeredAt().toString())
                .isEqualTo("2026-08-15T05:22:33Z");
    }

    @Test
    void evaluatesGetFindAndHistoryResponses() throws Exception {
        when(contract.evaluateTransaction("GetOriginal", ASSET_ID))
                .thenReturn(ORIGINAL_JSON.getBytes(StandardCharsets.UTF_8));
        when(contract.evaluateTransaction("FindBySha256", SHA256))
                .thenReturn(ORIGINAL_JSON.getBytes(StandardCharsets.UTF_8));
        String historyJson = "[{\"transactionId\":\"tx-1\","
                + "\"timestamp\":\"2026-08-15T05:22:33Z\","
                + "\"isDelete\":false,\"asset\":" + ORIGINAL_JSON.trim() + "}]";
        when(contract.evaluateTransaction("GetAssetHistory", ASSET_ID))
                .thenReturn(historyJson.getBytes(StandardCharsets.UTF_8));

        BlockchainOriginalResponse original = client.getOriginal(ASSET_ID);

        assertThat(original.sha256()).isEqualTo(SHA256);
        assertThat(client.findBySha256(SHA256)).contains(original);
        assertThat(client.getAssetHistory(ASSET_ID))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.transactionId()).isEqualTo("tx-1");
                    assertThat(entry.asset()).isEqualTo(original);
                });
    }

    @Test
    void emptyFindResponseMeansNoLedgerMatch() throws Exception {
        when(contract.evaluateTransaction("FindBySha256", SHA256))
                .thenReturn("null\n".getBytes(StandardCharsets.UTF_8));

        assertThat(client.findBySha256(SHA256)).isEmpty();
    }

    @Test
    void invalidLedgerJsonMapsToInvalidResponse() throws Exception {
        when(contract.evaluateTransaction("GetOriginal", ASSET_ID))
                .thenReturn("not-json".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> client.getOriginal(ASSET_ID))
                .isInstanceOfSatisfying(OriginalRegistryClientException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(OriginalRegistryClientException.Reason.INVALID_RESPONSE));
    }

    @Test
    void chaincodeDuplicateMessagesMapToSpecificConflictReasons() throws Exception {
        Proposal.Builder builder = org.mockito.Mockito.mock(Proposal.Builder.class);
        Proposal proposal = org.mockito.Mockito.mock(Proposal.class);
        when(contract.newProposal("RegisterOriginal")).thenReturn(builder);
        when(builder.addArguments(
                ASSET_ID, CREATOR_HASH, SHA256, "IMAGE", "VERIFIED", EVIDENCE_HASH))
                .thenReturn(builder);
        when(builder.build()).thenReturn(proposal);
        doThrow(new EndorseException(
                "tx-duplicate",
                Status.ABORTED.withDescription(
                        "original asset \"" + ASSET_ID + "\" already exists")
                        .asRuntimeException())).when(proposal).endorse();

        assertThatThrownBy(() -> client.registerOriginal(registrationRequest()))
                .isInstanceOfSatisfying(OriginalRegistryClientException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(OriginalRegistryClientException.Reason.DUPLICATE_ASSET));

        doThrow(new EndorseException(
                "tx-duplicate-sha",
                Status.ABORTED.withDescription(
                        "SHA-256 \"" + SHA256 + "\" is already registered")
                        .asRuntimeException())).when(proposal).endorse();
        assertThatThrownBy(() -> client.registerOriginal(registrationRequest()))
                .isInstanceOfSatisfying(OriginalRegistryClientException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(OriginalRegistryClientException.Reason.DUPLICATE_SHA256));
    }

    @Test
    void unavailableGatewayMapsToUnavailableReason() throws Exception {
        when(contract.evaluateTransaction("GetOriginal", ASSET_ID)).thenThrow(
                new GatewayException(Status.UNAVAILABLE.asRuntimeException()));

        assertThatThrownBy(() -> client.getOriginal(ASSET_ID))
                .isInstanceOfSatisfying(OriginalRegistryClientException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(OriginalRegistryClientException.Reason.UNAVAILABLE));
    }

    private OriginalRegistryClient.RegistrationRequest registrationRequest() {
        return new OriginalRegistryClient.RegistrationRequest(
                ASSET_ID, CREATOR_HASH, SHA256, "IMAGE", "VERIFIED", EVIDENCE_HASH);
    }
}
