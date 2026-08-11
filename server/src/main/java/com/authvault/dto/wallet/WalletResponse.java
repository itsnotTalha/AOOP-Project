package com.authvault.dto.wallet;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {

    private BigDecimal balance;
    private BigDecimal totalEarned;
    private BigDecimal totalSpent;
}
