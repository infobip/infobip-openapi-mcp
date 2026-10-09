package com.infobip.openapi.mcp.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class DownstreamApiRestClientsTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().port(0));
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldNotEncodeAlreadyPercentEncodedQueryParameterValue() {
        // Given
        var restClient = builder().build();
        wireMockServer.stubFor(
                get(urlEqualTo("/search?q=a%2Bb%20c")).willReturn(aResponse().withStatus(200)));

        // When: if the builder re-encoded an already-encoded value, "%" would become "%25"
        var response = restClient
                .get()
                .uri(uriBuilder ->
                        uriBuilder.path("/search").queryParam("q", "a%2Bb%20c").build())
                .retrieve()
                .toBodilessEntity();

        // Then
        then(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void shouldNotEncodeQueryParameterValueContainingReservedCharacters() {
        // Given
        var restClient = builder().build();
        wireMockServer.stubFor(
                get(urlEqualTo("/search?q=a&b")).willReturn(aResponse().withStatus(200)));

        // When: a query-string-reserved '&' inside a value would normally be encoded to "%26"
        var response = restClient
                .get()
                .uri(uriBuilder ->
                        uriBuilder.path("/search").queryParam("q", "a&b").build())
                .retrieve()
                .toBodilessEntity();

        // Then: the raw '&' passing through untouched proves no encoding was performed
        then(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void shouldApplyConnectAndReadTimeouts() {
        // Given: a very short read timeout, and a backend response delayed past it
        var restClient = DownstreamApiRestClients.builder(
                        "http://localhost:" + wireMockServer.port(), Duration.ofMillis(200), Duration.ofMillis(200))
                .build();
        wireMockServer.stubFor(get(urlEqualTo("/status"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(500)));

        // When / Then: the request times out instead of waiting for the delayed response
        thenThrownBy(() -> restClient.get().uri("/status").retrieve().toBodilessEntity())
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void shouldSendRequestBodyWithContentLengthInsteadOfChunkedTransferEncoding() {
        // Given
        var restClient = builder().build();
        wireMockServer.stubFor(post(urlEqualTo("/users"))
                .withHeader("Content-Length", matching("\\d+"))
                .withHeader("Transfer-Encoding", absent())
                .willReturn(aResponse().withStatus(200)));

        // When: a body of unknown length would be chunked, which the JDK client's h2c upgrade attempt
        // gets reset for by some servers (WireMock included)
        var response = restClient
                .post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "John Doe", "email", "john@example.com"))
                .retrieve()
                .toBodilessEntity();

        // Then
        then(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void shouldSupportPatchMethod() {
        // Given
        var restClient = builder().build();
        wireMockServer.stubFor(patch(urlEqualTo("/resource"))
                .withRequestBody(equalTo("{\"name\":\"updated\"}"))
                .willReturn(aResponse().withStatus(200)));

        // When
        var response = restClient
                .patch()
                .uri("/resource")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"updated\"}")
                .retrieve()
                .toBodilessEntity();

        // Then
        then(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void shouldFollowRedirectsForGetRequests() {
        // Given
        var restClient = builder().build();
        wireMockServer.stubFor(
                get(urlEqualTo("/old")).willReturn(aResponse().withStatus(302).withHeader("Location", "/new")));
        wireMockServer.stubFor(get(urlEqualTo("/new")).willReturn(aResponse().withStatus(200)));

        // When
        var response = restClient.get().uri("/old").retrieve().toBodilessEntity();

        // Then
        then(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private RestClient.Builder builder() {
        return DownstreamApiRestClients.builder(
                "http://localhost:" + wireMockServer.port(), DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);
    }
}
