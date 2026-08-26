package com.authvault.blockchain;

import com.authvault.dto.blockchain.BlockchainHistoryResponse;
import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.blockchain.BlockchainRegistrationResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.GatewayException;
import org.hyperledger.fabric.client.GatewayRuntimeException;
import org.hyperledger.fabric.client.Proposal;
import org.hyperledger.fabric.client.Transaction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "authvault.blockchain", name = "enabled", havingValue = "true")
public class FabricOriginalRegistryClient implements OriginalRegistryClient {

    private static final TypeReference<List<BlockchainHistoryResponse>> HISTORY_TYPE =
            new TypeReference<>() {
            };

    private final Contract contract;
    private final ObjectMapper objectMapper;

    public FabricOriginalRegistryClient(
            FabricGatewayProvider gatewayProvider,
            ObjectMapper objectMapper) {
        this.contract = gatewayProvider.getContract();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public BlockchainRegistrationResponse registerOriginal(RegistrationRequest request) {
        try {
            Proposal proposal = contract.newProposal("RegisterOriginal")
                    .addArguments(
                            request.assetId(),
                            request.creatorIdHash(),
                            request.sha256(),
                            request.assetType(),
                            request.verificationStatus(),
                            request.evidenceHash())
                    .build();
            Transaction transaction = proposal.endorse();
            byte[] result = transaction.submit();
            return new BlockchainRegistrationResponse(
                    transaction.getTransactionId(),
                    parseRequired(result, BlockchainOriginalResponse.class));
        } catch (GatewayException | GatewayRuntimeException | CommitException exception) {
            throw mapRegistrationFailure(exception);
        }
    }

    @Override
    public BlockchainOriginalResponse getOriginal(String assetId) {
        try {
            return parseRequired(
                    contract.evaluateTransaction("GetOriginal", assetId),
                    BlockchainOriginalResponse.class);
        } catch (GatewayException | GatewayRuntimeException exception) {
            throw mapQueryFailure(exception);
        }
    }

    @Override
    public Optional<BlockchainOriginalResponse> findBySha256(String sha256) {
        try {
            byte[] result = contract.evaluateTransaction("FindBySha256", sha256);
            if (isEmptyResult(result)) {
                return Optional.empty();
            }
            return Optional.of(parseRequired(result, BlockchainOriginalResponse.class));
        } catch (GatewayException | GatewayRuntimeException exception) {
            throw mapQueryFailure(exception);
        }
    }

    @Override
    public List<BlockchainHistoryResponse> getAssetHistory(String assetId) {
        try {
            byte[] result = contract.evaluateTransaction("GetAssetHistory", assetId);
            if (isEmptyResult(result)) {
                throw invalidResponse(null);
            }
            return objectMapper.readValue(result, HISTORY_TYPE);
        } catch (IOException exception) {
            throw invalidResponse(exception);
        } catch (GatewayException | GatewayRuntimeException exception) {
            throw mapQueryFailure(exception);
        }
    }

    private <T> T parseRequired(byte[] result, Class<T> type) {
        if (isEmptyResult(result)) {
            throw invalidResponse(null);
        }
        try {
            T parsed = objectMapper.readValue(result, type);
            if (parsed == null) {
                throw invalidResponse(null);
            }
            return parsed;
        } catch (IOException exception) {
            throw invalidResponse(exception);
        }
    }

    private boolean isEmptyResult(byte[] result) {
        if (result == null || result.length == 0) {
            return true;
        }
        String text = new String(result, StandardCharsets.UTF_8).trim();
        return text.isEmpty() || "null".equals(text);
    }

    private OriginalRegistryClientException mapRegistrationFailure(Throwable exception) {
        String message = fabricErrorMessage(exception).toLowerCase(Locale.ROOT);
        if (message.contains("original asset") && message.contains("already exists")) {
            return new OriginalRegistryClientException(
                    OriginalRegistryClientException.Reason.DUPLICATE_ASSET,
                    "The asset is already registered on the blockchain", exception);
        }
        if (message.contains("sha-256") && message.contains("already registered")) {
            return new OriginalRegistryClientException(
                    OriginalRegistryClientException.Reason.DUPLICATE_SHA256,
                    "The SHA-256 is already registered on the blockchain", exception);
        }
        if (isUnavailable(exception)) {
            return new OriginalRegistryClientException(
                    OriginalRegistryClientException.Reason.UNAVAILABLE,
                    "The blockchain registry is unavailable", exception);
        }
        return new OriginalRegistryClientException(
                OriginalRegistryClientException.Reason.REGISTRATION_FAILED,
                "Blockchain registration failed", exception);
    }

    private OriginalRegistryClientException mapQueryFailure(Throwable exception) {
        String message = fabricErrorMessage(exception).toLowerCase(Locale.ROOT);
        if (message.contains("original asset") && message.contains("does not exist")) {
            return new OriginalRegistryClientException(
                    OriginalRegistryClientException.Reason.ASSET_NOT_FOUND,
                    "Blockchain original not found", exception);
        }
        return new OriginalRegistryClientException(
                OriginalRegistryClientException.Reason.UNAVAILABLE,
                "The blockchain registry is unavailable", exception);
    }

    private OriginalRegistryClientException invalidResponse(Throwable cause) {
        return new OriginalRegistryClientException(
                OriginalRegistryClientException.Reason.INVALID_RESPONSE,
                "The blockchain registry returned an invalid response", cause);
    }

    private boolean isUnavailable(Throwable exception) {
        if (exception instanceof GatewayException gatewayException) {
            Status.Code code = gatewayException.getStatus().getCode();
            return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
        }
        if (exception instanceof GatewayRuntimeException gatewayException) {
            Status.Code code = gatewayException.getStatus().getCode();
            return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
        }
        return false;
    }

    private String fabricErrorMessage(Throwable exception) {
        StringBuilder message = new StringBuilder();
        if (exception.getMessage() != null) {
            message.append(exception.getMessage());
        }
        if (exception instanceof GatewayException gatewayException) {
            gatewayException.getDetails().forEach(detail ->
                    message.append(' ').append(detail.getMessage()));
        } else if (exception instanceof GatewayRuntimeException gatewayException) {
            gatewayException.getDetails().forEach(detail ->
                    message.append(' ').append(detail.getMessage()));
        }
        return message.toString();
    }
}
