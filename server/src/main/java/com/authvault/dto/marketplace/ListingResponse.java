package com.authvault.dto.marketplace;

import java.math.BigDecimal;
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
public class ListingResponse {

    private Long listingId;
    private String assetUuid;
    private UserResponse seller;
    private BigDecimal price;
    private String currency;
    private String status;
    private LocalDateTime listedAt;
}
