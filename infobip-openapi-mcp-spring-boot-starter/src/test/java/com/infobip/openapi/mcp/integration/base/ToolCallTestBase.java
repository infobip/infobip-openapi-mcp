package com.infobip.openapi.mcp.integration.base;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public abstract class ToolCallTestBase extends IntegrationTestBase {

    @Test
    void shouldCallTool() {
        withInitializedMcpClient(givenClient -> {
            // Given
            givenOpenAPISpecification("/openapi/base.json");
            getStaticWireMockServer()
                    .stubFor(get(urlEqualTo("/test")).willReturn(aResponse().withStatus(200)));

            // When
            var actualResponse = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("get_test")
                    .arguments(Map.of())
                    .build());

            // Then
            then(actualResponse.isError()).isFalse();
            then(actualResponse.structuredContent()).isNull();
        });
    }

    @ParameterizedTest
    @CsvSource({
        "HEAD, head_method, /methods/head",
        "OPTIONS, options_method, /methods/options",
        "GET, get_method, /methods/get",
        "POST, post_method, /methods/post",
        "PUT, put_method, /methods/put",
        "PATCH, patch_method, /methods/patch",
        "DELETE, delete_method, /methods/delete",
        "TRACE, trace_method, /methods/trace"
    })
    void shouldCallToolBackedByHttpMethod(String givenHttpMethod, String givenToolName, String givenPath) {
        withInitializedMcpClient(givenClient -> {
            // Given
            givenOpenAPISpecification("/openapi/http-methods.json");
            getStaticWireMockServer()
                    .stubFor(request(givenHttpMethod, urlEqualTo(givenPath))
                            .willReturn(aResponse().withStatus(200)));

            // When
            var actualResponse = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name(givenToolName)
                    .arguments(Map.of())
                    .build());

            // Then
            then(actualResponse.isError()).isFalse();
            getStaticWireMockServer().verify(1, requestedFor(givenHttpMethod, urlEqualTo(givenPath)));
        });
    }

    @Test
    void shouldCallToolAndTimeout() {
        withInitializedMcpClient(givenClient -> {
            // Given
            givenOpenAPISpecification("/openapi/base.json");
            getStaticWireMockServer()
                    .stubFor(get(urlEqualTo("/test"))
                            .willReturn(aResponse().withStatus(200).withFixedDelay(3500)));

            // When
            var thrown = catchThrowable(() -> givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("get_test")
                    .arguments(Map.of())
                    .build()));

            // Then
            then(thrown.getCause()).isInstanceOf(TimeoutException.class);
        });
    }

    @Test
    void shouldCallToolAndFail() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var givenErrorResponse = """
                    {
                          "error": {
                            "code": "500",
                            "message": "An unexpected error occurred."
                          }
                        }
                    """;

            givenOpenAPISpecification("/openapi/base.json");
            getStaticWireMockServer()
                    .stubFor(get(urlEqualTo("/test"))
                            .willReturn(aResponse().withStatus(500).withBody(givenErrorResponse)));

            // When
            var actualResponse = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("get_test")
                    .arguments(Map.of())
                    .build());

            // Then
            then(actualResponse.isError()).isTrue();
            then(actualResponse.content().size()).isNotZero();
            then(actualResponse.content().getFirst())
                    .isNotNull()
                    .isInstanceOf(TextContent.class)
                    .extracting(content -> ((TextContent) content).text())
                    .isEqualTo(givenErrorResponse);
        });
    }

    @Test
    void shouldCallToolAndFailWithErrorNotInOpenApiSpecification() {
        withInitializedMcpClient(givenClient -> {
            // Given
            var givenErrorResponse = """
                    {
                      "error": {
                        "code": "400",
                        "message": "Bad request."
                      }
                    }
                    """;

            givenOpenAPISpecification("/openapi/base.json");
            getStaticWireMockServer()
                    .stubFor(get(urlEqualTo("/test"))
                            .willReturn(aResponse().withStatus(400).withBody(givenErrorResponse)));

            // When
            var actualResponse = givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("get_test")
                    .arguments(Map.of())
                    .build());

            // Then
            then(actualResponse.isError()).isTrue();
            then(actualResponse.content().size()).isNotZero();
            then(actualResponse.content().getFirst())
                    .isNotNull()
                    .isInstanceOf(TextContent.class)
                    .extracting(content -> ((TextContent) content).text())
                    .isEqualTo(givenErrorResponse);
        });
    }
}
