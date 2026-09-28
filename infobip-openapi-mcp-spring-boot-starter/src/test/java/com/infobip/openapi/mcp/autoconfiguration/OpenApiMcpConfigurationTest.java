package com.infobip.openapi.mcp.autoconfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.infobip.openapi.mcp.config.ApiBaseUrlConfig;
import com.infobip.openapi.mcp.config.ApiBaseUrlProvider;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies that {@link OpenApiMcpConfiguration#toolHandlerRestClient} performs no URI encoding of its
 * own. Callers of this {@code RestClient} (e.g. {@code ToolHandler}) are responsible for encoding
 * query/path parameter values themselves before passing them in; the bean must pass those values
 * through unchanged, both when they're already percent-encoded and when they still contain characters
 * that would normally require encoding.
 */
@ExtendWith(MockitoExtension.class)
class OpenApiMcpConfigurationTest {

    @Mock
    private OpenApiRegistry openApiRegistry;

    private final OpenApiMcpConfiguration configuration = new OpenApiMcpConfiguration();
    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().port(0));
        wireMockServer.start();

        var openApi = new OpenAPI().servers(List.of(new Server().url("http://localhost:" + wireMockServer.port())));
        when(openApiRegistry.openApi()).thenReturn(openApi);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldNotEncodeAlreadyPercentEncodedQueryParameterValue() {
        // Given
        var restClient = configuration.toolHandlerRestClient(properties(), apiBaseUrlProvider());
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
        var restClient = configuration.toolHandlerRestClient(properties(), apiBaseUrlProvider());
        wireMockServer.stubFor(
                get(urlEqualTo("/search?q=a&b")).willReturn(aResponse().withStatus(200)));

        // When: a query-string-reserved '&' inside a value would normally be encoded to "%26";
        // an un-encoded stub match here proves the builder left it untouched
        var response = restClient
                .get()
                .uri(uriBuilder ->
                        uriBuilder.path("/search").queryParam("q", "a&b").build())
                .retrieve()
                .toBodilessEntity();

        // Then
        then(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private OpenApiMcpProperties properties() {
        return new OpenApiMcpProperties(
                URI.create("http://localhost/openapi.json"),
                null,
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private ApiBaseUrlProvider apiBaseUrlProvider() {
        return new ApiBaseUrlProvider(ApiBaseUrlConfig.parse(null), openApiRegistry);
    }
}
