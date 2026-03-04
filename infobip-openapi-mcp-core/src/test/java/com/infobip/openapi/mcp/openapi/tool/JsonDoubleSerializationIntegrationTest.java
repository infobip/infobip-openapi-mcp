package com.infobip.openapi.mcp.openapi.tool;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.error.DefaultErrorModelProvider;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import com.infobip.openapi.mcp.infrastructure.metrics.MetricService;
import com.infobip.openapi.mcp.infrastructure.metrics.NoOpMetricService;
import com.infobip.openapi.mcp.openapi.schema.DecomposedRequestData;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class JsonDoubleSerializationIntegrationTest {

    @Mock
    private OpenApiMcpProperties propertiesWithMitigationEnabled;

    @Mock
    private OpenApiMcpProperties propertiesWithMitigationDisabled;

    private MetricService metricService = new NoOpMetricService();

    private WireMockServer wireMockServer;
    private ToolHandler toolHandlerWithMitigationEnabled;
    private ToolHandler toolHandlerWithMitigationDisabled;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().port(0));
        wireMockServer.start();

        // Workaround for WireMock issue https://github.com/wiremock/wiremock/issues/2459
        // TODO: revisit this in MCP-111
        var http1factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
        var restClient = RestClient.builder()
                .requestFactory(http1factory)
                .baseUrl("http://localhost:" + wireMockServer.port())
                .build();

        // Setup mock properties with lenient stubbing to avoid unnecessary stubbing errors
        var toolsConfigEnabled = new OpenApiMcpProperties.Tools(null, null, true, null, null, null);
        var toolsConfigDisabled = new OpenApiMcpProperties.Tools(null, null, false, null, null, null);

        lenient().when(propertiesWithMitigationEnabled.tools()).thenReturn(toolsConfigEnabled);
        lenient().when(propertiesWithMitigationDisabled.tools()).thenReturn(toolsConfigDisabled);

        // Create ErrorModelWriter
        objectMapper = new ObjectMapper();
        var errorModelProvider = new DefaultErrorModelProvider();
        var errorModelWriter = new ErrorModelWriter(objectMapper, errorModelProvider);

        // Create handlers with mitigation enabled and disabled
        var emptyEnricherChain = new ApiRequestEnricherChain(List.of());
        toolHandlerWithMitigationEnabled = new ToolHandler(
                restClient, errorModelWriter, propertiesWithMitigationEnabled, emptyEnricherChain, metricService);
        toolHandlerWithMitigationDisabled = new ToolHandler(
                restClient, errorModelWriter, propertiesWithMitigationDisabled, emptyEnricherChain, metricService);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Nested
    class FeatureEnabledTests {

        @Test
        void shouldDetectAndRetryWithCorrectedJsonOnBadRequest() throws Exception {
            // Given - Double-serialized JSON (the problematic case)
            var inputJson = """
                    {
                      "messages": "[{\\"destinations\\":[{\\"to\\":\\"some destination\\"}],\\"content\\":{\\"text\\":\\"Hello\\"}}]"
                    }
                    """;

            // Expected corrected JSON (what it should be after mitigation)
            var correctedJson = """
                    {
                      "messages": [
                        {
                          "destinations": [{"to": "some destination"}],
                          "content": {"text": "Hello"}
                        }
                      ]
                    }
                    """;

            // Convert to objects for processing
            var doubleSerializedPayload = objectMapper.readValue(inputJson, Object.class);

            var fullOperation = new FullOperation("/send", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.withRequestBody(doubleSerializedPayload);

            // First call with double-serialized payload should return 400
            wireMockServer.stubFor(post(urlPathEqualTo("/send"))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/json"))
                    .withRequestBody(equalToJson(inputJson))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withBody("{\"error\":\"Bad request\",\"message\":\"Invalid JSON structure\"}")));

            // Second call with corrected payload should succeed
            wireMockServer.stubFor(post(urlPathEqualTo("/send"))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/json"))
                    .withRequestBody(equalToJson(correctedJson))
                    .willReturn(aResponse().withStatus(200).withBody("{\"messageId\":\"12345\",\"status\":\"sent\"}")));

            // When
            var result = toolHandlerWithMitigationEnabled.handleToolCall(
                    fullOperation, decomposedSchema, createTestContext());

            // Then - Should succeed after retry with corrected payload
            then(result.isError()).isFalse();
            var expectedResponse = """
                    {
                      "messageId": "12345",
                      "status": "sent"
                    }
                    """;
            JSONAssert.assertEquals(expectedResponse, extractTextContent(result.content()), JSONCompareMode.STRICT);

            // Verify both requests were made
            wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/send")).withRequestBody(equalToJson(inputJson)));
            wireMockServer.verify(
                    1, postRequestedFor(urlPathEqualTo("/send")).withRequestBody(equalToJson(correctedJson)));
        }

        @Test
        void shouldNotRetryWhenFirstRequestSucceeds() throws Exception {
            // Given - Valid JSON that should work on first try (no double serialization)
            var validJson = """
                    {
                      "messages": [
                        {
                          "destinations": [{"to": "destination"}],
                          "content": {"text": "Hello"}
                        }
                      ]
                    }
                    """;

            // Convert to object for processing
            var validPayload = objectMapper.readValue(validJson, Object.class);

            var fullOperation = new FullOperation("/send", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.withRequestBody(validPayload);

            wireMockServer.stubFor(post(urlPathEqualTo("/send"))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/json"))
                    .withRequestBody(equalToJson(validJson))
                    .willReturn(aResponse().withStatus(200).withBody("{\"messageId\":\"67890\",\"status\":\"sent\"}")));

            // When
            var result = toolHandlerWithMitigationEnabled.handleToolCall(
                    fullOperation, decomposedSchema, createTestContext());

            // Then - Should succeed on first try
            then(result.isError()).isFalse();
            var expectedResponse = """
                    {
                      "messageId": "67890",
                      "status": "sent"
                    }
                    """;
            JSONAssert.assertEquals(expectedResponse, extractTextContent(result.content()), JSONCompareMode.STRICT);

            // Verify only one request was made
            wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/send")));
        }

        @Test
        void shouldNotRetryWhenStatusIsNot400() throws Exception {
            // Given - JSON payload that fails with 403 (not 400)
            var inputJson = """
                    {
                      "invalidField": "value"
                    }
                    """;

            // Convert to object for processing
            var payload = objectMapper.readValue(inputJson, Object.class);

            var fullOperation = new FullOperation("/send", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.withRequestBody(payload);

            wireMockServer.stubFor(post(urlPathEqualTo("/send"))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/json"))
                    .withRequestBody(equalToJson(inputJson))
                    .willReturn(aResponse()
                            .withStatus(403)
                            .withBody("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}")));

            // When
            var result = toolHandlerWithMitigationEnabled.handleToolCall(
                    fullOperation, decomposedSchema, createTestContext());

            // Then - Should fail without retry (not 400 status)
            then(result.isError()).isTrue();
            var expectedErrorResponse = """
                    {
                      "error": "Forbidden",
                      "message": "Access denied"
                    }
                    """;
            JSONAssert.assertEquals(
                    expectedErrorResponse, extractTextContent(result.content()), JSONCompareMode.STRICT);

            // Verify only one request was made
            wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/send")));
        }

        @Test
        void shouldHandleComplexNestedDoubleSerializationCorrectly() throws Exception {
            // Given - Complex nested double-serialized JSON structure
            var inputJson = """
                    {
                      "data": "{\\"nested\\":\\"{\\\\\\"value\\\\\\":\\\\\\"deep\\\\\\",\\\\\\"number\\\\\\":42}\\",\\"other\\":\\"value\\"}",
                      "metadata": {"version": "1.0"}
                    }
                    """;

            // Expected corrected JSON structure
            var correctedJson = """
                    {
                      "data": {
                        "nested": {
                          "value": "deep",
                          "number": 42
                        },
                        "other": "value"
                      },
                      "metadata": {"version": "1.0"}
                    }
                    """;

            // Convert to objects for processing
            var outerPayload = objectMapper.readValue(inputJson, Object.class);

            var fullOperation = new FullOperation("/process", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.withRequestBody(outerPayload);

            // First call with double-serialized payload should return 400
            wireMockServer.stubFor(post(urlPathEqualTo("/process"))
                    .withRequestBody(equalToJson(inputJson))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withBody("{\"error\":\"Bad request\",\"message\":\"Malformed JSON data\"}")));

            // Second call with corrected payload should succeed
            wireMockServer.stubFor(post(urlPathEqualTo("/process"))
                    .withRequestBody(equalToJson(correctedJson))
                    .willReturn(aResponse().withStatus(200).withBody("{\"processed\":true,\"id\":\"nested-123\"}")));

            // When
            var result = toolHandlerWithMitigationEnabled.handleToolCall(
                    fullOperation, decomposedSchema, createTestContext());

            // Then - Should succeed after retry with fully corrected payload
            then(result.isError()).isFalse();
            var expectedResponse = """
                    {
                      "processed": true,
                      "id": "nested-123"
                    }
                    """;
            JSONAssert.assertEquals(expectedResponse, extractTextContent(result.content()), JSONCompareMode.STRICT);

            // Verify both requests were made
            wireMockServer.verify(2, postRequestedFor(urlPathEqualTo("/process")));
        }

        @Test
        void shouldHandleNetworkErrorDuringRetryGracefully() throws Exception {
            // Given - Double-serialized JSON that triggers retry, but retry fails with network error
            var inputJson = """
                    {
                      "messages": "[{\\"destinations\\":[{\\"to\\":\\"some destination\\"}],\\"content\\":{\\"text\\":\\"Hello\\"}}]"
                    }
                    """;

            var correctedJson = """
                    {
                      "messages": [
                        {
                          "destinations": [{"to": "some destination"}],
                          "content": {"text": "Hello"}
                        }
                      ]
                    }
                    """;

            var doubleSerializedPayload = objectMapper.readValue(inputJson, Object.class);

            var fullOperation = new FullOperation("/send", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.withRequestBody(doubleSerializedPayload);

            // First call with double-serialized payload should return 400 (triggers retry)
            wireMockServer.stubFor(post(urlPathEqualTo("/send"))
                    .withRequestBody(equalToJson(inputJson))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withBody("{\"error\":\"Bad request\",\"message\":\"Invalid JSON structure\"}")));

            // Second call (retry) should simulate connection reset - this will cause a network error
            wireMockServer.stubFor(post(urlPathEqualTo("/send"))
                    .withRequestBody(equalToJson(correctedJson))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            // When
            var result = toolHandlerWithMitigationEnabled.handleToolCall(
                    fullOperation, decomposedSchema, createTestContext());

            // Then - Should return 502 Bad Gateway for network error, not throw exception
            then(result.isError()).isTrue();
            var expectedErrorResponse = """
                    {
                      "error": "Bad Gateway",
                      "description": "The server received an invalid response from an upstream server."
                    }
                    """;
            JSONAssert.assertEquals(
                    expectedErrorResponse, extractTextContent(result.content()), JSONCompareMode.STRICT);
        }

        @Test
        void shouldReturnOriginalErrorWhenCorrectedPayloadAlsoFails() throws Exception {
            // Given - Double-serialized JSON that even after correction still fails
            var inputJson = """
                    {
                      "data": "{\\"required_field_missing\\":\\"value\\"}"
                    }
                    """;

            // Expected corrected JSON (but will still fail validation)
            var correctedJson = """
                    {
                      "data": {
                        "required_field_missing": "value"
                      }
                    }
                    """;

            // Convert to objects for processing
            var payload = objectMapper.readValue(inputJson, Object.class);

            var fullOperation =
                    new FullOperation("/validate", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.withRequestBody(payload);

            // First call with double-serialized payload returns 400 indicating JSON issue
            wireMockServer.stubFor(post(urlPathEqualTo("/validate"))
                    .withRequestBody(equalToJson(inputJson))
                    .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"Invalid JSON structure\"}")));

            // Second call with corrected payload also returns 400 but for different reason
            wireMockServer.stubFor(post(urlPathEqualTo("/validate"))
                    .withRequestBody(equalToJson(correctedJson))
                    .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"Missing required field: name\"}")));

            // When
            var result = toolHandlerWithMitigationEnabled.handleToolCall(
                    fullOperation, decomposedSchema, createTestContext());

            // Then - Should return the second error (from corrected payload attempt)
            then(result.isError()).isTrue();
            var expectedErrorResponse = """
                    {
                      "error": "Missing required field: name"
                    }
                    """;
            JSONAssert.assertEquals(
                    expectedErrorResponse, extractTextContent(result.content()), JSONCompareMode.STRICT);

            // Verify both requests were made
            wireMockServer.verify(2, postRequestedFor(urlPathEqualTo("/validate")));
        }
    }

    @Nested
    class FeatureDisabledTests {

        @Test
        void shouldNotRetryWhenFeatureIsDisabled() throws Exception {
            // Given - Double-serialized JSON that would normally be mitigated
            var inputJson = """
                    {
                      "messages": "[{\\"destinations\\":[{\\"to\\":\\"destination\\"}],\\"content\\":{\\"text\\":\\"Hello\\"}}]"
                    }
                    """;

            // Convert to object for processing
            var payload = objectMapper.readValue(inputJson, Object.class);

            var fullOperation = new FullOperation("/send", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.withRequestBody(payload);

            wireMockServer.stubFor(post(urlPathEqualTo("/send"))
                    .withRequestBody(equalToJson(inputJson))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withBody("{\"error\":\"Bad request\",\"message\":\"Invalid JSON structure\"}")));

            // When
            var result = toolHandlerWithMitigationDisabled.handleToolCall(
                    fullOperation, decomposedSchema, createTestContext());

            // Then - Should fail without retry
            then(result.isError()).isTrue();
            var expectedErrorResponse = """
                    {
                      "error": "Bad request",
                      "message": "Invalid JSON structure"
                    }
                    """;
            JSONAssert.assertEquals(
                    expectedErrorResponse, extractTextContent(result.content()), JSONCompareMode.STRICT);

            // Verify only one request was made
            wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/send")));
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void shouldHandleEmptyRequestBodyGracefully() throws Exception {
            // Given - Request with no body
            var fullOperation = new FullOperation("/status", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.empty();

            wireMockServer.stubFor(get(urlPathEqualTo("/status"))
                    .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"Bad request\"}")));

            // When - Feature enabled handler
            var result = toolHandlerWithMitigationEnabled.handleToolCall(
                    fullOperation, decomposedSchema, createTestContext());

            // Then - Should not retry (no body to mitigate)
            then(result.isError()).isTrue();
            var expectedErrorResponse = """
                    {
                      "error": "Bad request"
                    }
                    """;
            JSONAssert.assertEquals(
                    expectedErrorResponse, extractTextContent(result.content()), JSONCompareMode.STRICT);
            wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/status")));
        }

        @Test
        void shouldHandleNonJsonPayloadsGracefully() throws Exception {
            // Given - String payload (not JSON object)
            var fullOperation = new FullOperation("/upload", PathItem.HttpMethod.POST, new Operation(), new OpenAPI());
            var decomposedSchema = DecomposedRequestData.withRequestBody("simple string payload");

            wireMockServer.stubFor(post(urlPathEqualTo("/upload"))
                    .withRequestBody(equalTo("simple string payload"))
                    .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"Invalid format\"}")));

            // When
            var result = toolHandlerWithMitigationEnabled.handleToolCall(
                    fullOperation, decomposedSchema, createTestContext());

            // Then - Should not retry (no JSON to mitigate)
            then(result.isError()).isTrue();
            var expectedErrorResponse = """
                    {
                      "error": "Invalid format"
                    }
                    """;
            JSONAssert.assertEquals(
                    expectedErrorResponse, extractTextContent(result.content()), JSONCompareMode.STRICT);
            wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/upload")));
        }
    }

    /**
     * Helper method to create a simple test context without HTTP request.
     */
    private McpRequestContext createTestContext() {
        return new McpRequestContext();
    }

    /**
     * Helper method to extract text content from McpSchema content objects
     */
    private String extractTextContent(List<McpSchema.Content> content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        var firstContentItem = content.getFirst();
        if (firstContentItem instanceof McpSchema.TextContent textContentItem) {
            return textContentItem.text();
        }
        return firstContentItem.toString();
    }
}
