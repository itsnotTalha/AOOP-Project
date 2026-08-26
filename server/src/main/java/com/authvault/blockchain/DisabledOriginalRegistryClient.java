package com.authvault.blockchain;

import com.authvault.dto.blockchain.BlockchainHistoryResponse;
import com.authvault.dto.blockchain.BlockchainOriginalResponse;
import com.authvault.dto.blockchain.BlockchainRegistrationResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(
        prefix = "authvault.blockchain",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class DisabledOriginalRegistryClient implements OriginalRegistryClient {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public BlockchainRegistrationResponse registerOriginal(RegistrationRequest request) {
        throw disabled();
    }

    @Override
    public BlockchainOriginalResponse getOriginal(String assetId) {
        throw disabled();
    }

    @Override
    public Optional<BlockchainOriginalResponse> findBySha256(String sha256) {
        throw disabled();
    }

    @Override
    public List<BlockchainHistoryResponse> getAssetHistory(String assetId) {
        throw disabled();
    }

    private OriginalRegistryClientException disabled() {
        return new OriginalRegistryClientException(
                OriginalRegistryClientException.Reason.DISABLED,
                "Blockchain integration is disabled");
    }
}
