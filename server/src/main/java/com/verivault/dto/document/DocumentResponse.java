package com.verivault.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

    private String assetUuid;
    private String extractedText;
    private String semanticHash;
    private Integer pageCount;
    private String language;
}
