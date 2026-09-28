package com.infobip.openapi.mcp.resource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.auth.CredentialProvider;
import com.infobip.openapi.mcp.config.DownstreamApiRestClients;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.infrastructure.metrics.MicrometerMetricService;
import com.infobip.openapi.mcp.infrastructure.metrics.NoOpMetricService;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
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

        restClient = DownstreamApiRestClients.builder(
                        "http://localhost:" + wireMockServer.port(), Duration.ofSeconds(5), Duration.ofSeconds(5))
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
    void shouldReturnBinaryBackendResponseAsBase64EncodedBlobResourceContents() {
        // Given
        var pngBytes = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        wireMockServer.stubFor(get(urlPathEqualTo("/logo"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "image/png")
                        .withBody(pngBytes)));
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/logo": {
                  "get": {
                    "operationId": "getLogo"
                  }
                }
              }
            }
            """);
        var handler = handlerWithCredential(context -> Optional.empty());
        var request = new McpSchema.ReadResourceRequest("api://logo");

        // When
        var result =
                handler.handleResourceRead(fullOperation, "getLogo", "image/png", request, new McpRequestContext());

        // Then
        then(result.contents())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(new McpSchema.BlobResourceContents(
                        "api://logo", "image/png", Base64.getEncoder().encodeToString(pngBytes)));
    }

    @Test
    void shouldReturnContentsProducedByResourceContentsConverter() {
        // Given
        wireMockServer.stubFor(get(urlPathEqualTo("/status"))
                .willReturn(aResponse().withHeader("X-Trace", "abc").withBody("UP")));
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
        var receivedResponses = new ArrayList<ResourceResponse>();
        ResourceContentsConverter converter = (response, context) -> {
            receivedResponses.add(response);
            return new McpSchema.TextResourceContents(response.uri(), "text/custom", "converted");
        };
        var handler = new ResourceHandler(
                restClient,
                context -> Optional.empty(),
                noOpEnricherChain,
                metricService,
                resourceUriBuilder,
                converter);
        var request = new McpSchema.ReadResourceRequest("api://status");

        // When
        var result =
                handler.handleResourceRead(fullOperation, "getStatus", MIME_TYPE, request, new McpRequestContext());

        // Then
        then(result.contents())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(new McpSchema.TextResourceContents("api://status", "text/custom", "converted"));
        then(receivedResponses).singleElement().satisfies(response -> {
            then(response.uri()).isEqualTo("api://status");
            then(response.resourceName()).isEqualTo("getStatus");
            then(response.declaredMimeType()).isEqualTo(MIME_TYPE);
            then(response.fullOperation()).isSameAs(fullOperation);
            then(response.statusCode().value()).isEqualTo(200);
            then(response.headers().getFirst("X-Trace")).isEqualTo("abc");
            then(response.body()).isEqualTo("UP".getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    void shouldThrowResourceReadExceptionWhenResourceContentsConverterFails() {
        // Given
        wireMockServer.stubFor(
                get(urlPathEqualTo("/status")).willReturn(aResponse().withBody("UP")));
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
        ResourceContentsConverter converter = (response, context) -> {
            throw new IllegalStateException("unsupported content");
        };
        var handler = new ResourceHandler(
                restClient,
                context -> Optional.empty(),
                noOpEnricherChain,
                metricService,
                resourceUriBuilder,
                converter);
        var request = new McpSchema.ReadResourceRequest("api://status");

        // When & Then
        thenThrownBy(() -> handler.handleResourceRead(
                        fullOperation, "getStatus", MIME_TYPE, request, new McpRequestContext()))
                .isInstanceOf(ResourceReadException.class)
                .hasMessageContaining("getStatus")
                .hasMessageContaining("unsupported content");
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

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            # resourceUri                  | expectedBackendUrl
            api://search?q=%2B385          | /search?q=%2B385
            api://search?q=a+b             | /search?q=a%2Bb
            api://search?q=John%20Doe      | /search?q=John%20Doe
            api://search?q=%7Bid%7D        | /search?q=%7Bid%7D
            api://search?q=a%26b%3Dc       | /search?q=a%26b%3Dc
            """)
    void shouldEncodeQueryParameterValuesWhenCallingBackend(String resourceUri, String expectedBackendUrl) {
        // Given
        wireMockServer.stubFor(
                get(urlEqualTo(expectedBackendUrl)).willReturn(aResponse().withBody("[]")));
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/search": {
                  "get": {
                    "operationId": "search",
                    "parameters": [
                      { "name": "q", "in": "query", "schema": { "type": "string" } }
                    ]
                  }
                }
              }
            }
            """);
        var handler = handlerWithCredential(context -> Optional.empty());
        var request = new McpSchema.ReadResourceRequest(resourceUri);

        // When
        var result = handler.handleResourceRead(fullOperation, "search", MIME_TYPE, request, new McpRequestContext());

        // Then
        then(result.contents())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(new McpSchema.TextResourceContents(resourceUri, MIME_TYPE, "[]"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            # resourceUri                     | expectedBackendUrl
            api://search/%2B385                | /search/%2B385
            api://search/a+b                   | /search/a%2Bb
            api://search/John%20Doe            | /search/John%20Doe
            api://search/%7Bid%7D              | /search/%7Bid%7D
            api://search/a%26b%3Dc             | /search/a&b=c
            """)
    void shouldEncodePathVariableValuesWhenCallingBackend(String resourceUri, String expectedBackendUrl) {
        // Given
        wireMockServer.stubFor(
                get(urlEqualTo(expectedBackendUrl)).willReturn(aResponse().withBody("[]")));
        var fullOperation = fullOperation("""
            {
              "openapi": "3.1.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {
                "/search/{q}": {
                  "get": {
                    "operationId": "search",
                    "parameters": [
                      { "name": "q", "in": "path", "required": true, "schema": { "type": "string" } }
                    ]
                  }
                }
              }
            }
            """);
        var handler = handlerWithCredential(context -> Optional.empty());
        var request = new McpSchema.ReadResourceRequest(resourceUri);

        // When
        var result = handler.handleResourceRead(fullOperation, "search", MIME_TYPE, request, new McpRequestContext());

        // Then
        then(result.contents())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(new McpSchema.TextResourceContents(resourceUri, MIME_TYPE, "[]"));
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

    @ParameterizedTest
    @CsvSource({"200, false", "500, true"})
    void shouldRecordResourceCallAndReadCallDurations(int backendStatus, boolean expectedIsError) {
        // Given
        wireMockServer.stubFor(
                get(urlPathEqualTo("/status")).willReturn(aResponse().withStatus(backendStatus)));
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
        var meterRegistry = new SimpleMeterRegistry();
        var handler = new ResourceHandler(
                restClient,
                context -> Optional.empty(),
                noOpEnricherChain,
                new MicrometerMetricService(meterRegistry, operation -> "getStatus"),
                resourceUriBuilder,
                new DefaultResourceContentsConverter());
        var request = new McpSchema.ReadResourceRequest("api://status");

        // When
        try {
            handler.handleResourceRead(fullOperation, "getStatus", MIME_TYPE, request, new McpRequestContext());
        } catch (ResourceReadException ignored) {
            // error path is asserted through metrics
        }

        // Then
        then(meterRegistry
                        .get("com.infobip.openapi.resource.call.duration")
                        .tag("resource_name", "getStatus")
                        .tag("is_error", String.valueOf(expectedIsError))
                        .timer()
                        .count())
                .isEqualTo(1);
        then(meterRegistry
                        .get("com.infobip.openapi.resource.read.call.duration")
                        .tag("resource_name", "getStatus")
                        .tag("status_code", String.valueOf(backendStatus))
                        .timer()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void shouldRecordResourceCallDurationAsErrorWhenCredentialCannotBeProvided() {
        // Given
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
        var meterRegistry = new SimpleMeterRegistry();
        var handler = new ResourceHandler(
                restClient,
                context -> {
                    throw new IllegalStateException("credential source unavailable");
                },
                noOpEnricherChain,
                new MicrometerMetricService(meterRegistry, operation -> "getStatus"),
                resourceUriBuilder,
                new DefaultResourceContentsConverter());
        var request = new McpSchema.ReadResourceRequest("api://status");

        // When & Then
        thenThrownBy(() -> handler.handleResourceRead(
                        fullOperation, "getStatus", MIME_TYPE, request, new McpRequestContext()))
                .isInstanceOf(ResourceReadException.class);
        then(meterRegistry
                        .get("com.infobip.openapi.resource.call.duration")
                        .tag("is_error", "true")
                        .timer()
                        .count())
                .isEqualTo(1);
        then(meterRegistry
                        .find("com.infobip.openapi.resource.read.call.duration")
                        .timer())
                .isNull();
    }

    private ResourceHandler handlerWithCredential(CredentialProvider credentialProvider) {
        return new ResourceHandler(
                restClient,
                credentialProvider,
                noOpEnricherChain,
                metricService,
                resourceUriBuilder,
                new DefaultResourceContentsConverter());
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
