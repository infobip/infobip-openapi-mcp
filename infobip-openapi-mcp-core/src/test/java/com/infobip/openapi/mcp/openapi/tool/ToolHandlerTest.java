package com.infobip.openapi.mcp.openapi.tool;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.enricher.XForwardedForEnricher;
import com.infobip.openapi.mcp.error.DefaultErrorModelProvider;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.infrastructure.metrics.NoOpMetricService;
import com.infobip.openapi.mcp.openapi.schema.DecomposedRequestData;
import com.infobip.openapi.mcp.util.XForwardedForCalculator;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class ToolHandlerTest {

    private static final int TIMEOUT_MS = 200;

    // Expected error responses as constants
    private static final String EXPECTED_BAD_GATEWAY_ERROR_JSON =
            "{\"error\":\"Bad Gateway\",\"description\":\"The server received an invalid response from an upstream server.\"}";

    @Mock
    private OpenApiMcpProperties properties;

    private MetricService metricService = new NoOpMetricService();

    private WireMockServer wireMockServer;
    private ToolHandler toolHandler;
    private ErrorModelWriter errorModelWriter;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().port(0));
        wireMockServer.start();

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);

        var restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .requestFactory(requestFactory)
                .build();

        // Setup mock properties
        var toolsConfig = new OpenApiMcpProperties.Tools(null, null, true, null);
        lenient().when(properties.tools()).thenReturn(toolsConfig);

        // Create actual ErrorModelWriter with DefaultErrorModelProvider
        var objectMapper = new ObjectMapper();
        var errorModelProvider = new DefaultErrorModelProvider();
        errorModelWriter = new ErrorModelWriter(objectMapper, errorModelProvider);

        // Create enrichers (auth is handled explicitly, not via enricher)
        var xffCalculator = new XForwardedForCalculator();
        var xffEnricher = new XForwardedForEnricher(xffCalculator);
        var enricherChain = new ApiRequestEnricherChain(List.of(xffEnricher));

        toolHandler = new ToolHandler(restClient, errorModelWriter, properties, enricherChain, metricService);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Nested
    class SuccessfulCalls {

        @Test
        void shouldHandleGetRequestWithQueryParameters() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = new DecomposedRequestData(
                    new DecomposedRequestData.ParametersByType(
                            Map.of(), Map.of("limit", "10", "offset", "0"), Map.of(), Map.of()),
                    null);
            var responseBody = "{\"users\":[{\"id\":1,\"name\":\"John Doe\",\"email\":\"john@example.com\"}]}";

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withQueryParam("limit", equalTo("10"))
                    .withQueryParam("offset", equalTo("0"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandleGetRequestWithCollectionQueryParametersRespectingOpenApiDefaults() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = new DecomposedRequestData(
                    new DecomposedRequestData.ParametersByType(
                            Map.of(), Map.of("role", List.of("admin", "user")), Map.of(), Map.of()),
                    null);
            var responseBody = "{\"users\":[{\"id\":1,\"name\":\"John Doe\"}]}";

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withQueryParam("role", equalTo("admin"))
                    .withQueryParam("role", equalTo("user"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandlePostRequestWithRequestBody() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var requestBodyData = Map.<String, Object>of("name", "John Doe", "email", "john@example.com");
            var decomposedSchema = DecomposedRequestData.withRequestBody(requestBodyData);
            var responseBody = "{\"id\":1,\"name\":\"John Doe\",\"email\":\"john@example.com\"}";

            wireMockServer.stubFor(post(urlPathEqualTo("/users"))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/json"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withRequestBody(equalToJson("{\"name\":\"John Doe\",\"email\":\"john@example.com\"}"))
                    .willReturn(aResponse().withStatus(201).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandlePostRequestWithJsonArrayRequestBody() {
            // Given
            var fullOperation =
                    new FullOperation("/users/batch", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var requestBodyData = List.of(
                    Map.of("name", "John Doe", "email", "john@example.com"),
                    Map.of("name", "Jane Smith", "email", "jane@example.com"));
            var decomposedSchema = DecomposedRequestData.withRequestBody(requestBodyData);
            var responseBody =
                    "{\"created\":2,\"users\":[{\"id\":1,\"name\":\"John Doe\"},{\"id\":2,\"name\":\"Jane Smith\"}]}";

            wireMockServer.stubFor(post(urlPathEqualTo("/users/batch"))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/json"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withRequestBody(
                            equalToJson(
                                    "[{\"name\":\"John Doe\",\"email\":\"john@example.com\"},{\"name\":\"Jane Smith\",\"email\":\"jane@example.com\"}]"))
                    .willReturn(aResponse().withStatus(201).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandlePostRequestWithStringRequestBody() {
            // Given
            var fullOperation =
                    new FullOperation("/users/import", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var requestBodyData = "John Doe,john@example.com";
            var decomposedSchema = DecomposedRequestData.withRequestBody(requestBodyData);
            var responseBody = "{\"imported\":2,\"message\":\"Users imported successfully\"}";

            wireMockServer.stubFor(post(urlPathEqualTo("/users/import"))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/json"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withRequestBody(equalTo("John Doe,john@example.com"))
                    .willReturn(aResponse().withStatus(201).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandlePutRequestWithPathParameters() {
            // Given
            var fullOperation =
                    new FullOperation("/users/{id}", PathItem.HttpMethod.PUT, new Operation(), new OpenAPI());
            var requestBodyData = Map.<String, Object>of("name", "Jane Doe");
            var decomposedSchema = DecomposedRequestData.withParametersAndBodyContent(
                    new DecomposedRequestData.ParametersByType(Map.of("id", "123"), Map.of(), Map.of(), Map.of()),
                    requestBodyData);
            var responseBody = "{\"id\":123,\"name\":\"Jane Doe\"}";

            wireMockServer.stubFor(put(urlPathEqualTo("/users/123"))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/json"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withRequestBody(equalToJson("{\"name\":\"Jane Doe\"}"))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandleRequestWithMultiplePathParameters() {
            // Given
            var fullOperation = new FullOperation(
                    "/users/{userId}/orders/{orderId}", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = new DecomposedRequestData(
                    new DecomposedRequestData.ParametersByType(
                            Map.of("userId", "42", "orderId", 1001), Map.of(), Map.of(), Map.of()),
                    null);
            var responseBody = "{\"orderId\":1001,\"userId\":42,\"item\":\"Laptop\",\"quantity\":1}";

            wireMockServer.stubFor(get(urlPathEqualTo("/users/42/orders/1001"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandleRequestWithHeaders() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = new DecomposedRequestData(
                    new DecomposedRequestData.ParametersByType(
                            Map.of(),
                            Map.of(),
                            Map.of("X-Custom-Header", "custom-value", "X-Another-Custom-Header", "another-value"),
                            Map.of()),
                    null);
            var responseBody = "Response with headers";

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader("X-Custom-Header", equalTo("custom-value"))
                    .withHeader("X-Another-Custom-Header", equalTo("another-value"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandleRequestWithMultipleHeaderValuesRespectingOpenAPIDefaults() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = new DecomposedRequestData(
                    new DecomposedRequestData.ParametersByType(
                            Map.of(), Map.of(), Map.of("X-Multi-Value-Header", List.of("value1", "value2")), Map.of()),
                    null);
            var responseBody = "Response with multiple header values";

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader("X-Multi-Value-Header", equalTo("value1,value2"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandleRequestWithCookies() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = new DecomposedRequestData(
                    new DecomposedRequestData.ParametersByType(
                            Map.of(), Map.of(), Map.of(), Map.of("sessionId", "abc123", "preference", "dark-mode")),
                    null);
            var responseBody = "Response with cookies";

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withCookie("sessionId", equalTo("abc123"))
                    .withCookie("preference", equalTo("dark-mode"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandleRequestWithMultipleCookieValuesRespectingOpenAPIDefaults() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = new DecomposedRequestData(
                    new DecomposedRequestData.ParametersByType(
                            Map.of(), Map.of(), Map.of(), Map.of("multiCookie", List.of("value1", "value2"))),
                    null);
            var responseBody = "Response with multiple cookie values";

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withCookie("multiCookie", equalTo("value1"))
                    .withCookie("multiCookie", equalTo("value2"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandleRequestWithAuthenticationHeader() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();
            var responseBody = "Authenticated response";

            var mockRequest = new MockHttpServletRequest();
            mockRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token123");

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token123"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext(mockRequest));

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandleDeleteRequestWithoutBody() {
            // Given
            var fullOperation =
                    new FullOperation("/users/{id}", PathItem.HttpMethod.DELETE, new Operation(), new OpenAPI());
            var decomposedSchema = new DecomposedRequestData(
                    new DecomposedRequestData.ParametersByType(Map.of("id", "123"), Map.of(), Map.of(), Map.of()),
                    null);

            wireMockServer.stubFor(delete(urlPathEqualTo("/users/123"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(204)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo("{\"message\":\"Tool call completed successfully\"}");
            then(result.isError()).isFalse();
        }

        @Test
        void shouldHandleComplexRequestWithAllParameterTypes() {
            // Given
            var fullOperation =
                    new FullOperation("/users/{id}/posts", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var requestBodyData = Map.<String, Object>of("title", "New Post", "content", "Post content");
            var decomposedSchema = DecomposedRequestData.withParametersAndBodyContent(
                    new DecomposedRequestData.ParametersByType(
                            Map.of("id", "123"),
                            Map.of("draft", "true", "notify", "false"),
                            Map.of("X-Request-ID", "req-123"),
                            Map.of("session", "sess-456", "theme", "dark")),
                    requestBodyData);
            var responseBody = "Post created";

            var mockRequest = new MockHttpServletRequest();
            mockRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token123");
            mockRequest.setRemoteAddr("127.0.0.1");

            wireMockServer.stubFor(post(urlPathEqualTo("/users/123/posts"))
                    .withQueryParam("draft", equalTo("true"))
                    .withQueryParam("notify", equalTo("false"))
                    .withHeader("X-Request-ID", equalTo("req-123"))
                    .withHeader("X-Forwarded-For", equalTo("127.0.0.1"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token123"))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/json"))
                    .withCookie("session", equalTo("sess-456"))
                    .withCookie("theme", equalTo("dark"))
                    .withRequestBody(equalToJson("{\"title\":\"New Post\",\"content\":\"Post content\"}"))
                    .willReturn(aResponse().withStatus(201).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext(mockRequest));

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldNotOverrideAcceptHeaderWhenProvidedAsHeaderParameter() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = new DecomposedRequestData(
                    new DecomposedRequestData.ParametersByType(
                            Map.of(),
                            Map.of(),
                            Map.of("Accept", "application/xml", "X-Custom-Header", "custom-value"),
                            Map.of()),
                    null);
            var responseBody = "Response with custom accept header";

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader(
                            "Accept",
                            equalTo("application/xml")) // Should use the provided Accept header, not application/json
                    .withHeader("X-Custom-Header", equalTo("custom-value"))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldNotOverrideAcceptHeaderWhenProvidedAsHeaderParameterCaseInsensitive() {
            // Given - Test with lowercase "accept" header
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = new DecomposedRequestData(
                    new DecomposedRequestData.ParametersByType(
                            Map.of(),
                            Map.of(),
                            Map.of("accept", "text/plain", "X-Custom-Header", "custom-value"), // lowercase "accept"
                            Map.of()),
                    null);
            var responseBody = "Response with custom accept header (case insensitive)";

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader(
                            "Accept",
                            equalTo("text/plain")) // Should use the provided accept header, not application/json
                    .withHeader("X-Custom-Header", equalTo("custom-value"))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void shouldHandleNotFoundErrorException() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();
            var errorResponse = "{\"error\":\"Not found\"}";

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(404).withBody(errorResponse)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(errorResponse);
            then(result.isError()).isTrue();
        }

        @Test
        void shouldHandleUnauthorizedErrorException() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();
            var errorResponse = "{\"error\":\"Unauthorized\"}";

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(401).withBody(errorResponse)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(errorResponse);
            then(result.isError()).isTrue();
        }

        @Test
        void shouldHandleForbiddenErrorException() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();
            var errorResponse = "{\"error\":\"Forbidden\"}";

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(403).withBody(errorResponse)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(errorResponse);
            then(result.isError()).isTrue();
        }

        @Test
        void shouldHandleBadRequestErrorException() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.withRequestBody(Map.of("invalid", "data"));
            var errorResponse = "{\"error\":\"Bad request\"}";

            wireMockServer.stubFor(post(urlPathEqualTo("/users"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(400).withBody(errorResponse)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(errorResponse);
            then(result.isError()).isTrue();
        }

        @Test
        void shouldHandleHttpServerErrorException() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();
            var errorResponse = "{\"error\":\"Internal server error\"}";

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(500).withBody(errorResponse)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(errorResponse);
            then(result.isError()).isTrue();
        }

        @Test
        void shouldHandleReadTimeout() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withFixedDelay(TIMEOUT_MS + 100))); // This will cause a read timeout

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(EXPECTED_BAD_GATEWAY_ERROR_JSON);
            then(result.isError()).isTrue();
        }

        @Test
        void shouldHandleConnectionTimeout() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();

            // Create a separate RestClient that connects to a non-existent port to force connection timeout
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(TIMEOUT_MS);
            requestFactory.setReadTimeout(TIMEOUT_MS);

            var restClientWithBadPort = RestClient.builder()
                    .baseUrl("http://localhost:99999") // Non-existent port
                    .requestFactory(requestFactory)
                    .build();

            // Create mock properties for this test
            var propertiesDisabled = org.mockito.Mockito.mock(OpenApiMcpProperties.class);
            var toolsConfigDisabled = new OpenApiMcpProperties.Tools(null, null, false, null);
            lenient().when(propertiesDisabled.tools()).thenReturn(toolsConfigDisabled);

            var emptyEnricherChain = new ApiRequestEnricherChain(List.of());
            var toolHandlerWithBadPort = new ToolHandler(
                    restClientWithBadPort, errorModelWriter, propertiesDisabled, emptyEnricherChain, metricService);

            // When
            var result = toolHandlerWithBadPort.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(EXPECTED_BAD_GATEWAY_ERROR_JSON);
            then(result.isError()).isTrue();
        }
    }

    @Nested
    class RequestContextProviderIntegration {

        @Test
        void shouldNotAddAuthorizationHeaderWhenRequestHasNoAuthHeader() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();
            var responseBody = "Response without auth";

            var mockRequest = new MockHttpServletRequest();
            // No Authorization header set

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader(HttpHeaders.AUTHORIZATION, absent())
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext(mockRequest));

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldNotAddAuthorizationHeaderWhenRequestHasEmptyAuthHeader() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();
            var responseBody = "Response without auth";

            var mockRequest = new MockHttpServletRequest();
            mockRequest.addHeader(HttpHeaders.AUTHORIZATION, "");

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader(HttpHeaders.AUTHORIZATION, absent())
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext(mockRequest));

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldAddAuthorizationHeaderWhenRequestHasValidToken() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();
            var responseBody = "Authorized response";
            var authToken = "Bearer valid-token-123";

            var mockRequest = new MockHttpServletRequest();
            mockRequest.addHeader(HttpHeaders.AUTHORIZATION, authToken);

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader(HttpHeaders.AUTHORIZATION, equalTo(authToken))
                    .withHeader("Accept", equalTo("application/json"))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext(mockRequest));

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldNotAddXFFHeaderWhenRequestHasNoRemoteAddr() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();
            var responseBody = "Response without XFF";

            var mockRequest = new MockHttpServletRequest();
            // Explicitly set remote address to empty string to simulate no remote address
            mockRequest.setRemoteAddr("");

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader(HttpHeaders.AUTHORIZATION, absent())
                    .withHeader("X-Forwarded-For", absent())
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext(mockRequest));

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldAddXFFHeaderWhenRequestHasRemoteAddr() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();
            var responseBody = "Response with XFF";
            var clientIp = "203.0.113.1";

            var mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr(clientIp);

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader("X-Forwarded-For", equalTo(clientIp))
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext(mockRequest));

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }

        @Test
        void shouldNotAddHeadersWhenRequestContextIsNull() {
            // Given
            var fullOperation = new FullOperation("/users", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();
            var responseBody = "Response without context";

            // No RequestContextHolder setup - simulates no HTTP context

            wireMockServer.stubFor(get(urlPathEqualTo("/users"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader(HttpHeaders.AUTHORIZATION, absent())
                    .withHeader("X-Forwarded-For", absent())
                    .willReturn(aResponse().withStatus(200).withBody(responseBody)));

            // When
            var result = toolHandler.handleToolCall(fullOperation, decomposedSchema, createTestContext());

            // Then
            then(extractTextContent(result.content())).isEqualTo(responseBody);
            then(result.isError()).isFalse();
        }
    }

    /**
     * Helper method to create a simple test context without HTTP request.
     */
    private McpRequestContext createTestContext() {
        return new McpRequestContext(null, null, null, null);
    }

    /**
     * Helper method to create a test context with HTTP request.
     */
    private McpRequestContext createTestContext(MockHttpServletRequest request) {
        return new McpRequestContext(request, null, null, null);
    }

    /**
     * Helper method to extract text content from McpSchema content objects
     */
    private String extractTextContent(List<McpSchema.Content> content) {
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("Response content is empty!");
        }
        var firstContentItem = content.getFirst();
        if (firstContentItem instanceof McpSchema.TextContent textContentItem) {
            return textContentItem.text();
        }
        return firstContentItem.toString();
    }
}
