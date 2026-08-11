package com.authvault.dto.blockchain;

import java.time.LocalDateTime;

import com.authvault.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockchainBlockResponse {

    private Long blockIndex;
    private String assetUuid;
    private UserResponse owner;
    private String action;
    private String currentHash;
    private String previousHash;
    private LocalDateTime timestamp;
}
