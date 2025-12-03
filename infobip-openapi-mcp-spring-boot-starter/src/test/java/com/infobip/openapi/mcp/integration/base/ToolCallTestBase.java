package com.infobip.openapi.mcp.integration.base;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

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

    @Test
    void shouldCallToolAndTimeout() {
        withInitializedMcpClient(givenClient -> {
            // Given
            givenOpenAPISpecification("/openapi/base.json");
            getStaticWireMockServer()
                    .stubFor(get(urlEqualTo("/test"))
                            .willReturn(aResponse().withStatus(200).withFixedDelay(2500)));

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
            var givenErrorResponse =
                    """
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
            var givenErrorResponse =
                    """
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
