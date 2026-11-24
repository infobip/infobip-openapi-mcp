package com.infobip.openapi.mcp.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.integration.base.PartialAuthIntegrationTestBase;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * The headline MCP-393 behavior at MCP-client level: with {@code PARTIAL_AUTH_CLAUDE_COMPATIBLE} mode an unauthenticated
 * client can initialize and list tools (in {@code ALL_OR_NOTHING} mode every request dies with a 401),
 * and tool calls the backing API accepts run normally without any credentials.
 */
@ActiveProfiles({"integration", "integration-security"})
@TestPropertySource(
        properties = {
            "infobip.openapi.mcp.security.auth.mode=PARTIAL_AUTH_CLAUDE_COMPATIBLE",
            "spring.ai.mcp.server.protocol = stateless"
        })
class PartialAuthClaudeAuthFlowIntegrationTest extends PartialAuthIntegrationTestBase {

    @Test
    void shouldInitializeAndListToolsWithoutCredentials() {
        // Given
        givenOpenAPISpecification("/openapi/base.json");

        try (var givenMcpClient = givenMcpClientWithAuthHeader("")) {
            // When
            givenMcpClient.initialize();
            var tools = givenMcpClient.listTools().tools();

            // Then
            then(tools).isNotEmpty();
            getStaticWireMockServer().verify(0, getRequestedFor(urlEqualTo("/auth/validate")));
        }
    }

    @Test
    void shouldCallApiAcceptedToolWithoutCredentials() {
        // Given
        givenOpenAPISpecification("/openapi/base.json");
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/test"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"data\":\"public\"}")));

        try (var givenMcpClient = givenMcpClientWithAuthHeader("")) {
            // When
            givenMcpClient.initialize();
            var result = givenMcpClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("get_test")
                    .arguments(Map.of())
                    .build());

            // Then
            then(result.isError()).isFalse();
            getStaticWireMockServer().verify(0, getRequestedFor(urlEqualTo("/auth/validate")));
        }
    }
}
