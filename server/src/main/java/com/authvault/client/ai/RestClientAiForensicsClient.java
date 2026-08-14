package com.authvault.client.ai;

import com.authvault.config.AiServiceProperties;
import com.authvault.dto.ai.AiHealthResponse;
import com.authvault.dto.ai.AiImageAnalysisResponse;
import com.authvault.dto.ai.AiImageComparisonResponse;
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

import java.util.function.Supplier;

@Component
public class RestClientAiForensicsClient implements AiForensicsClient {

    private final AiServiceProperties properties;
    private final RestClient restClient;
    private final RestClient analysisRestClient;

    @Autowired
    public RestClientAiForensicsClient(
            RestClient.Builder restClientBuilder,
            AiServiceProperties properties) {
        this(
                buildRestClient(restClientBuilder, properties, properties.getReadTimeout()),
                buildRestClient(
                        restClientBuilder,
                        properties,
                        properties.getAnalysisReadTimeout()),
                properties);
    }

    RestClientAiForensicsClient(RestClient restClient, AiServiceProperties properties) {
        this(restClient, restClient, properties);
    }

    RestClientAiForensicsClient(
            RestClient restClient,
            RestClient analysisRestClient,
            AiServiceProperties properties) {
        this.properties = properties;
        this.restClient = restClient;
        this.analysisRestClient = analysisRestClient;
    }

    private static RestClient buildRestClient(
            RestClient.Builder restClientBuilder,
            AiServiceProperties properties,
            java.time.Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(readTimeout);

        return restClientBuilder
                .clone()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public AiClientResult<AiHealthResponse> health() {
        return execute(() -> restClient.get()
                .uri("/health")
                .retrieve()
                .body(AiHealthResponse.class));
    }

    @Override
    public AiClientResult<AiImageAnalysisResponse> analyzeImage(
            Resource controlledImage,
            MediaType mediaType) {
        if (!properties.isEnabled()) {
            return AiClientResult.disabled();
        }
        Assert.notNull(controlledImage, "Controlled image resource is required");

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(safeImageMediaType(mediaType));
        partHeaders.setContentDisposition(ContentDisposition.formData()
                .name("file")
                .filename("analysis-image")
                .build());

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(controlledImage, partHeaders));
        return execute(() -> analysisRestClient.post()
                .uri("/v1/analyze/image")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(AiImageAnalysisResponse.class));
    }

    @Override
    public AiClientResult<AiImageComparisonResponse> compareImages(
            Resource controlledReference,
            MediaType referenceMediaType,
            Resource controlledTarget,
            MediaType targetMediaType) {
        if (!properties.isEnabled()) {
            return AiClientResult.disabled();
        }
        Assert.notNull(controlledReference, "Controlled reference image resource is required");
        Assert.notNull(controlledTarget, "Controlled target image resource is required");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("reference", imagePart(
                "reference", controlledReference, referenceMediaType));
        body.add("target", imagePart("target", controlledTarget, targetMediaType));
        return execute(() -> restClient.post()
                .uri("/v1/compare/images")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(AiImageComparisonResponse.class));
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

    private <T> AiClientResult<T> execute(Supplier<T> request) {
        if (!properties.isEnabled()) {
            return AiClientResult.disabled();
        }

        try {
            T body = request.get();
            return body == null
                    ? AiClientResult.unavailable()
                    : AiClientResult.success(body);
        } catch (RestClientException exception) {
            return AiClientResult.unavailable();
        }
    }

    private MediaType safeImageMediaType(MediaType mediaType) {
        if (MediaType.IMAGE_JPEG.equals(mediaType) || MediaType.IMAGE_PNG.equals(mediaType)) {
            return mediaType;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
