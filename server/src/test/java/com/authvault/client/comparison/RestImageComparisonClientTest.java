package com.authvault.client.comparison;

import com.authvault.config.ImageComparisonProperties;
import com.authvault.dto.comparison.ImageComparisonResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestImageComparisonClientTest {

    private ImageComparisonProperties properties;
    private MockRestServiceServer server;
    private RestImageComparisonClient client;

    @BeforeEach
    void setUp() {
        properties = new ImageComparisonProperties();
        properties.setEnabled(true);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestImageComparisonClient(builder.build(), properties);
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

        ComparisonClientResult<ImageComparisonResponse> result = client.compareImages(
                new ByteArrayResource("REFERENCE_BYTES".getBytes()),
                MediaType.IMAGE_PNG,
                new ByteArrayResource("TARGET_BYTES".getBytes()),
                MediaType.IMAGE_JPEG);

        assertThat(result.status()).isEqualTo(ComparisonClientResult.Status.SUCCESS);
        assertThat(result.body().alignment().method()).isEqualTo("ORB_HOMOGRAPHY");
        assertThat(result.body().difference().changedAreaRatio()).isEqualTo(0.083);
        assertThat(result.body().mask().base64()).isEqualTo("bWFzaw==");
        server.verify();
    }

    @Test
    void representsUnavailableServiceAsControlledResult() {
        server.expect(requestTo("http://localhost:8001/v1/compare/images"))
                .andExpect(method(POST))
                .andRespond(withException(new IOException("connection refused")));

        ComparisonClientResult<ImageComparisonResponse> result = client.compareImages(
                new ByteArrayResource(new byte[]{1}),
                MediaType.IMAGE_PNG,
                new ByteArrayResource(new byte[]{2}),
                MediaType.IMAGE_PNG);

        assertThat(result.status()).isEqualTo(ComparisonClientResult.Status.UNAVAILABLE);
        assertThat(result.body()).isNull();
        server.verify();
    }

    @Test
    void doesNotCallServiceWhenComparisonIsDisabled() {
        properties.setEnabled(false);

        ComparisonClientResult<ImageComparisonResponse> result = client.compareImages(
                new ByteArrayResource(new byte[]{1}),
                MediaType.IMAGE_PNG,
                new ByteArrayResource(new byte[]{2}),
                MediaType.IMAGE_PNG);

        assertThat(result.status()).isEqualTo(ComparisonClientResult.Status.DISABLED);
        assertThat(result.body()).isNull();
        server.verify();
    }
}
