package com.authvault.client.ai;

import com.authvault.config.AiServiceProperties;
import com.authvault.dto.ai.AiImageAnalysisResponse;
import com.authvault.dto.ai.AiImageComparisonResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class RestClientAiForensicsClientTest {

    private AiServiceProperties properties;
    private MockRestServiceServer server;
    private RestClientAiForensicsClient client;

    @BeforeEach
    void setUp() {
        properties = new AiServiceProperties();
        properties.setEnabled(true);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientAiForensicsClient(builder.build(), properties);
    }

    @Test
    void deserializesNoModelAnalysisContractWithoutInventedScores() {
        String response = """
                {
                  "analysisVersion": "1",
                  "aiGeneration": {
                    "performed": false,
                    "status": "MODEL_NOT_CONFIGURED"
                  },
                  "manipulation": {
                    "performed": false,
                    "localizationAvailable": false,
                    "status": "MODEL_NOT_CONFIGURED"
                  }
                }
                """;
        server.expect(once(), requestTo("http://localhost:8001/v1/analyze/image"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        AiClientResult<AiImageAnalysisResponse> result = client.analyzeImage(
                new org.springframework.core.io.ByteArrayResource(new byte[]{1, 2, 3}),
                MediaType.IMAGE_PNG);

        assertThat(result.status()).isEqualTo(AiClientResult.Status.SUCCESS);
        assertThat(result.body().analysisVersion()).isEqualTo("1");
        assertThat(result.body().aiGeneration().performed()).isFalse();
        assertThat(result.body().aiGeneration().rawSyntheticScore()).isNull();
        assertThat(result.body().manipulation().manipulationScore()).isNull();
        assertThat(result.body().manipulation().status()).isEqualTo("MODEL_NOT_CONFIGURED");
        server.verify();
    }

    @Test
    void deserializesCompletedUfdAnalysisMetadataAndScores() {
        String response = """
                {
                  "analysisVersion": "1",
                  "aiGeneration": {
                    "performed": true,
                    "status": "COMPLETED",
                    "modelName": "UNIVERSAL_FAKE_DETECT",
                    "modelVersion": "UFD_CVPR2023_CLIP_VIT_L14_FC_V1",
                    "rawLogit": 0.75,
                    "rawSyntheticScore": 0.6791787,
                    "decisionThreshold": 0.5,
                    "modelSignal": "SYNTHETIC_LEANING",
                    "calibrationStatus": "NOT_CALIBRATED",
                    "calibratedSyntheticProbability": null
                  },
                  "manipulation": {
                    "performed": true,
                    "status": "COMPLETED",
                    "modelName": "TRUFOR",
                    "modelVersion": "TRUFOR_CVPR2023_RELEASED_V1",
                    "manipulationScore": 0.82,
                    "decisionThreshold": 0.5,
                    "modelSignal": "ELEVATED_MANIPULATION_SIGNAL",
                    "localizationAvailable": true,
                    "suspiciousAreaRatio": 0.25,
                    "reliableSuspiciousAreaRatio": 0.2,
                    "anomalyMapPngBase64": "YW5vbWFseQ==",
                    "reliabilityMapPngBase64": "cmVsaWFiaWxpdHk=",
                    "suspiciousMaskPngBase64": "bWFzaw=="
                  }
                }
                """;
        server.expect(once(), requestTo("http://localhost:8001/v1/analyze/image"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        AiClientResult<AiImageAnalysisResponse> result = client.analyzeImage(
                new org.springframework.core.io.ByteArrayResource(new byte[]{1, 2, 3}),
                MediaType.IMAGE_PNG);

        assertThat(result.body().aiGeneration().modelName())
                .isEqualTo("UNIVERSAL_FAKE_DETECT");
        assertThat(result.body().aiGeneration().rawLogit()).isEqualTo(0.75);
        assertThat(result.body().aiGeneration().rawSyntheticScore()).isEqualTo(0.6791787);
        assertThat(result.body().aiGeneration().calibratedSyntheticProbability()).isNull();
        assertThat(result.body().manipulation().modelName()).isEqualTo("TRUFOR");
        assertThat(result.body().manipulation().manipulationScore()).isEqualTo(0.82);
        assertThat(result.body().manipulation().reliableSuspiciousAreaRatio())
                .isEqualTo(0.2);
        assertThat(result.body().manipulation().suspiciousMaskPngBase64())
                .isEqualTo("bWFzaw==");
        server.verify();
    }

    @Test
    void representsUnavailableServiceAsControlledResult() {
        server.expect(requestTo("http://localhost:8001/health"))
                .andExpect(method(GET))
                .andRespond(withException(new IOException("connection refused")));

        AiClientResult<?> result = client.health();

        assertThat(result.status()).isEqualTo(AiClientResult.Status.UNAVAILABLE);
        assertThat(result.body()).isNull();
        server.verify();
    }

    @Test
    void sendsBothControlledImagesAndDeserializesComparison() {
        String response = """
                {
                  "comparisonVersion": "1",
                  "alignment": {
                    "status": "ALIGNED",
                    "method": "ORB_HOMOGRAPHY",
                    "keypointsReference": 80,
                    "keypointsTarget": 75,
                    "goodMatches": 42,
                    "inliers": 31,
                    "inlierRatio": 0.738
                  },
                  "difference": {
                    "performed": true,
                    "changedAreaRatio": 0.083,
                    "meanAbsoluteDifference": 12.6,
                    "structuralSimilarity": null
                  },
                  "mask": {
                    "available": true,
                    "format": "png",
                    "base64": "bWFzaw=="
                  },
                  "status": "COMPLETED"
                }
                """;
        server.expect(once(), requestTo("http://localhost:8001/v1/compare/images"))
                .andExpect(method(POST))
                .andExpect(request -> {
                    org.springframework.mock.http.client.MockClientHttpRequest mockRequest =
                            (org.springframework.mock.http.client.MockClientHttpRequest) request;
                    String multipart = mockRequest.getBodyAsString();
                    assertThat(multipart).contains("name=\"reference\"");
                    assertThat(multipart).contains("name=\"target\"");
                    assertThat(multipart).contains("REFERENCE_BYTES");
                    assertThat(multipart).contains("TARGET_BYTES");
                })
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        AiClientResult<AiImageComparisonResponse> result = client.compareImages(
                new org.springframework.core.io.ByteArrayResource("REFERENCE_BYTES".getBytes()),
                MediaType.IMAGE_PNG,
                new org.springframework.core.io.ByteArrayResource("TARGET_BYTES".getBytes()),
                MediaType.IMAGE_JPEG);

        assertThat(result.status()).isEqualTo(AiClientResult.Status.SUCCESS);
        assertThat(result.body().alignment().method()).isEqualTo("ORB_HOMOGRAPHY");
        assertThat(result.body().difference().changedAreaRatio()).isEqualTo(0.083);
        assertThat(result.body().mask().base64()).isEqualTo("bWFzaw==");
        server.verify();
    }

    @Test
    void doesNotCallServiceWhenIntegrationIsDisabled() {
        properties.setEnabled(false);

        AiClientResult<?> result = client.health();

        assertThat(result.status()).isEqualTo(AiClientResult.Status.DISABLED);
        assertThat(result.body()).isNull();
        server.verify();
    }
}
