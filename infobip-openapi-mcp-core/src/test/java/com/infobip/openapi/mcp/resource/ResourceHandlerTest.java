package com.infobip.openapi.mcp.resource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.auth.CredentialProvider;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.infrastructure.metrics.NoOpMetricService;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class ResourceHandlerTest {

    private static final String MIME_TYPE = "application/json";

    private final OpenAPIV3Parser parser = new OpenAPIV3Parser();
    private final ResourceUriBuilder resourceUriBuilder = new ResourceUriBuilder("api");
    private final ApiRequestEnricherChain noOpEnricherChain = new ApiRequestEnricherChain(List.of());
    private final MetricService metricService = new NoOpMetricService();

    private WireMockServer wireMockServer;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().port(0));
        wireMockServer.start();
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldReturnBackendResponseBodyAsTextResourceContents() {
        // Given
        wireMockServer.stubFor(
                get(urlPathEqualTo("/status")).willReturn(aResponse().withBody("{\"status\":\"UP\"}")));
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/status": {
                  "get": {
                    "operationId": "getStatus"
                  }
                }
              }
            }
            """);
        var handler = handlerWithCredential(context -> Optional.empty());
        var request = new McpSchema.ReadResourceRequest("api://status");

        // When
        var result =
                handler.handleResourceRead(fullOperation, "getStatus", MIME_TYPE, request, new McpRequestContext());

        // Then
        then(result.contents()).hasSize(1);
        var contents = (McpSchema.TextResourceContents) result.contents().getFirst();
        then(contents.uri()).isEqualTo("api://status");
        then(contents.mimeType()).isEqualTo(MIME_TYPE);
        then(contents.text()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void shouldSubstitutePathVariablesWhenCallingBackend() {
        // Given
        wireMockServer.stubFor(
                get(urlPathEqualTo("/users/123")).willReturn(aResponse().withBody("{\"id\":\"123\"}")));
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users/{id}": {
                  "get": {
                    "operationId": "getUserById",
                    "parameters": [
                      { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } }
                    ]
                  }
                }
              }
            }
            """);
        var handler = handlerWithCredential(context -> Optional.empty());
        var request = new McpSchema.ReadResourceRequest("api://users/123");

        // When
        var result =
                handler.handleResourceRead(fullOperation, "getUserById", MIME_TYPE, request, new McpRequestContext());

        // Then
        var contents = (McpSchema.TextResourceContents) result.contents().getFirst();
        then(contents.text()).isEqualTo("{\"id\":\"123\"}");
    }

    @Test
    void shouldForwardQueryParametersToBackend() {
        // Given
        wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                .withQueryParam("expand", equalTo("profile"))
                .willReturn(aResponse().withBody("[]")));
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/users": {
                  "get": {
                    "operationId": "getUsers",
                    "parameters": [
                      { "name": "expand", "in": "query", "schema": { "type": "string" } }
                    ]
                  }
                }
              }
            }
            """);
        var handler = handlerWithCredential(context -> Optional.empty());
        var request = new McpSchema.ReadResourceRequest("api://users?expand=profile");

        // When
        var result = handler.handleResourceRead(fullOperation, "getUsers", MIME_TYPE, request, new McpRequestContext());

        // Then
        var contents = (McpSchema.TextResourceContents) result.contents().getFirst();
        then(contents.text()).isEqualTo("[]");
    }

    @Test
    void shouldForwardCredentialAsAuthorizationHeader() {
        // Given
        wireMockServer.stubFor(get(urlPathEqualTo("/status"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token"))
                .willReturn(aResponse().withBody("{}")));
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/status": {
                  "get": {
                    "operationId": "getStatus"
                  }
                }
              }
            }
            """);
        var handler = handlerWithCredential(context -> Optional.of("Bearer token"));
        var request = new McpSchema.ReadResourceRequest("api://status");

        // When
        var result =
                handler.handleResourceRead(fullOperation, "getStatus", MIME_TYPE, request, new McpRequestContext());

        // Then
        then(result.contents()).hasSize(1);
    }

    @Test
    void shouldThrowResourceReadExceptionWhenBackendReturnsErrorStatus() {
        // Given
        wireMockServer.stubFor(get(urlPathEqualTo("/status"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/status": {
                  "get": {
                    "operationId": "getStatus"
                  }
                }
              }
            }
            """);
        var handler = handlerWithCredential(context -> Optional.empty());
        var request = new McpSchema.ReadResourceRequest("api://status");

        // When & Then
        thenThrownBy(() -> handler.handleResourceRead(
                        fullOperation, "getStatus", MIME_TYPE, request, new McpRequestContext()))
                .isInstanceOf(ResourceReadException.class)
                .hasMessageContaining("Failed to read resource 'getStatus'")
                .hasMessageContaining("GET /status");
    }

    private ResourceHandler handlerWithCredential(CredentialProvider credentialProvider) {
        return new ResourceHandler(
                restClient, credentialProvider, noOpEnricherChain, metricService, resourceUriBuilder);
    }

    private FullOperation fullOperation(String jsonSpec) {
        var openApi = parseOpenAPI(jsonSpec);
        var pathEntry = openApi.getPaths().entrySet().iterator().next();
        var operationEntry =
                pathEntry.getValue().readOperationsMap().entrySet().iterator().next();
        return new FullOperation(pathEntry.getKey(), operationEntry.getKey(), operationEntry.getValue(), openApi);
    }

    private OpenAPI parseOpenAPI(String jsonSpec) {
        return parser.readContents(jsonSpec).getOpenAPI();
    }
}
