package com.authvault.client.comparison;

import com.authvault.config.ImageComparisonProperties;
import com.authvault.dto.comparison.ImageComparisonResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestImageComparisonClient implements ImageComparisonClient {

    private final ImageComparisonProperties properties;
    private final RestClient restClient;

    @Autowired
    public RestImageComparisonClient(
            RestClient.Builder restClientBuilder,
            ImageComparisonProperties properties) {
        this(buildRestClient(restClientBuilder, properties), properties);
    }

    RestImageComparisonClient(
            RestClient restClient,
            ImageComparisonProperties properties) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(
            RestClient.Builder restClientBuilder,
            ImageComparisonProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return restClientBuilder
                .clone()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ComparisonClientResult<ImageComparisonResponse> compareImages(
            Resource controlledReference,
            MediaType referenceMediaType,
            Resource controlledTarget,
            MediaType targetMediaType) {
        if (!properties.isEnabled()) {
            return ComparisonClientResult.disabled();
        }
        Assert.notNull(controlledReference, "Controlled reference image resource is required");
        Assert.notNull(controlledTarget, "Controlled target image resource is required");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("reference", imagePart(
                "reference", controlledReference, referenceMediaType));
        body.add("target", imagePart("target", controlledTarget, targetMediaType));

        try {
            ImageComparisonResponse response = restClient.post()
                    .uri("/v1/compare/images")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ImageComparisonResponse.class);
            return response == null
                    ? ComparisonClientResult.unavailable()
                    : ComparisonClientResult.success(response);
        } catch (RestClientException exception) {
            return ComparisonClientResult.unavailable();
        }
    }

    private HttpEntity<Resource> imagePart(
            String name,
            Resource controlledImage,
            MediaType mediaType) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(safeImageMediaType(mediaType));
        partHeaders.setContentDisposition(ContentDisposition.formData()
                .name(name)
                .filename(name + "-image")
                .build());
        return new HttpEntity<>(controlledImage, partHeaders);
    }

    private MediaType safeImageMediaType(MediaType mediaType) {
        if (MediaType.IMAGE_JPEG.equals(mediaType) || MediaType.IMAGE_PNG.equals(mediaType)) {
            return mediaType;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
