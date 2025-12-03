package com.infobip.openapi.mcp.integration.base;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;

public abstract class ParameterPackingTestBase extends IntegrationTestBase {

    @Test
    void shouldListToolsWithParameters() {
        withInitializedMcpClient(givenClient -> {
            // Given
            givenOpenAPISpecification("/openapi/parameters.json");

            // When
            var actualResponse = givenClient.listTools();

            // Then
            then(actualResponse.tools()).hasSize(2);
            then(actualResponse.tools())
                    .extracting("name")
                    .containsExactlyInAnyOrder("test_operation_params_get", "test_operation_params_post");
            var getTool = findToolByName(actualResponse, "test_operation_params_get");
            then(getTool.inputSchema().properties())
                    .containsKeys("pathParam", "queryParam", "headerParam", "cookieParam");
            var postTool = findToolByName(actualResponse, "test_operation_params_post");
            then(postTool.inputSchema().properties()).containsKeys("_params", "_body");
            then(((Map<String, Map<String, ?>>)
                                    postTool.inputSchema().properties().get("_params"))
                            .get("properties"))
                    .containsKeys("pathParam", "queryParam", "headerParam", "cookieParam");
            then(((Map<String, Map<String, ?>>)
                                    postTool.inputSchema().properties().get("_body"))
                            .get("properties"))
                    .containsKeys("bodyParam");
        });
    }

    @Test
    void shouldCallGetToolWithMinimalParams() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var givenPathParam = "givenPathParam";
            givenOpenAPISpecification("/openapi/parameters.json");
            var givenApiResponse = """
                    {"resParam": "ok"}""";
            getStaticWireMockServer()
                    .stubFor(get(urlEqualTo("/test/%s".formatted(givenPathParam)))
                            .willReturn(aResponse().withStatus(200).withBody(givenApiResponse)));

            // When
            var actualToolResponse = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("test_operation_params_get")
                    .arguments(Map.of("pathParam", givenPathParam))
                    .build());

            // Then
            thenTollResponseMatchesApiResponse(actualToolResponse, givenApiResponse);
        });
    }

    @Test
    void shouldCallGetToolWithAllParams() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var givenPathParam = "givenPathParam";
            var givenQueryParam = "givenQueryParam";
            var givenHeaderParam = "givenHeaderParam";
            var givenCookieParam = "givenCookieParam";
            givenOpenAPISpecification("/openapi/parameters.json");
            var givenApiResponse = """
                    {"resParam": "ok"}""";
            var givenApiCallUrl = "/test/%s?queryParam=%s".formatted(givenPathParam, givenQueryParam);
            getStaticWireMockServer()
                    .stubFor(get(urlEqualTo(givenApiCallUrl))
                            .withHeader("headerParam", equalTo(givenHeaderParam))
                            .withCookie("cookieParam", equalTo(givenCookieParam))
                            .willReturn(aResponse().withStatus(200).withBody(givenApiResponse)));

            // When
            var actualToolResponse = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("test_operation_params_get")
                    .arguments(Map.of(
                            "pathParam", givenPathParam,
                            "queryParam", givenQueryParam,
                            "headerParam", givenHeaderParam,
                            "cookieParam", givenCookieParam))
                    .build());

            // Then
            thenTollResponseMatchesApiResponse(actualToolResponse, givenApiResponse);
        });
    }

    @Test
    void shouldCallPostToolWithMinimalParams() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var givenPathParam = "givenPathParam";
            givenOpenAPISpecification("/openapi/parameters.json");
            var givenApiResponse = """
                    {"resParam": "ok"}""";
            getStaticWireMockServer()
                    .stubFor(post(urlEqualTo("/test/%s".formatted(givenPathParam)))
                            .willReturn(aResponse().withStatus(200).withBody(givenApiResponse)));

            // When
            var actualToolResponse = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("test_operation_params_post")
                    .arguments(Map.of("_params", Map.of("pathParam", givenPathParam)))
                    .build());

            // Then
            thenTollResponseMatchesApiResponse(actualToolResponse, givenApiResponse);
        });
    }

    @Test
    void shouldCallPostToolWithAllParams() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var givenPathParam = "givenPathParam";
            var givenQueryParam = "givenQueryParam";
            var givenHeaderParam = "givenHeaderParam";
            var givenCookieParam = "givenCookieParam";
            var givenBodyParam = "givenBodyParam";
            givenOpenAPISpecification("/openapi/parameters.json");
            var givenApiResponse = """
                    {"resParam": "ok"}""";
            var givenApiCallUrl = "/test/%s?queryParam=%s".formatted(givenPathParam, givenQueryParam);
            getStaticWireMockServer()
                    .stubFor(post(urlEqualTo(givenApiCallUrl))
                            .withHeader("headerParam", equalTo(givenHeaderParam))
                            .withCookie("cookieParam", equalTo(givenCookieParam))
                            .withRequestBody(equalToJson("""
                                    {"bodyParam": "%s"}
                                    """.formatted(givenBodyParam)))
                            .willReturn(aResponse().withStatus(200).withBody(givenApiResponse)));

            // When
            var actualToolResponse = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("test_operation_params_post")
                    .arguments(Map.of(
                            "_params",
                                    Map.of(
                                            "pathParam", givenPathParam,
                                            "queryParam", givenQueryParam,
                                            "headerParam", givenHeaderParam,
                                            "cookieParam", givenCookieParam),
                            "_body", Map.of("bodyParam", givenBodyParam)))
                    .build());

            // Then
            thenTollResponseMatchesApiResponse(actualToolResponse, givenApiResponse);
        });
    }

    private McpSchema.Tool findToolByName(McpSchema.ListToolsResult actualResponse, String name) {
        return actualResponse.tools().stream()
                .filter(tool -> tool.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private void thenTollResponseMatchesApiResponse(McpSchema.CallToolResult actualResponse, String givenResponse) {
        then(actualResponse.isError()).isFalse();
        then(actualResponse.content().size()).isNotZero();
        then(actualResponse.content().getFirst())
                .isNotNull()
                .isInstanceOf(McpSchema.TextContent.class)
                .extracting(content -> ((McpSchema.TextContent) content).text())
                .isEqualTo(givenResponse);
    }
}
