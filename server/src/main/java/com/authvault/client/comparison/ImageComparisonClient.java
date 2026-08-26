package com.authvault.client.comparison;

import com.authvault.dto.comparison.ImageComparisonResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public interface ImageComparisonClient {

    ComparisonClientResult<ImageComparisonResponse> compareImages(
            Resource controlledReference,
            MediaType referenceMediaType,
            Resource controlledTarget,
            MediaType targetMediaType);
}
