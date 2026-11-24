package com.infobip.openapi.mcp.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.integration.base.PartialAuthIntegrationTestBase;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Pins the documented v1 limitation of {@code PARTIAL_AUTH_CLAUDE_COMPATIBLE} mode: response remapping into HTTP challenges
 * only works on the STATELESS protocol. On STREAMABLE the server stays functional (no breakage) but an
 * API authorization failure remains an MCP-level {@code isError} result — the remapping signal is a
 * server-internal request attribute, invisible on the wire — and therefore cannot trigger a client
 * login flow.
 */
@ActiveProfiles({"integration", "integration-security"})
@TestPropertySource(
        properties = {
            "infobip.openapi.mcp.security.auth.mode=PARTIAL_AUTH_CLAUDE_COMPATIBLE",
            "spring.ai.mcp.server.protocol = streamable"
        })
class PartialAuthClaudeStreamablePassthroughTest extends PartialAuthIntegrationTestBase {

    @Test
    void shouldKeepApiAuthErrorAsMcpLevelErrorOnStreamableProtocol() {
        // Given
        givenOpenAPISpecification("/openapi/base.json");
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/test"))
                        .willReturn(aResponse()
                                .withStatus(401)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"apiError\":\"denied\"}")));

        try (var givenMcpClient = givenMcpClientWithAuthHeader("")) {
            // When
            givenMcpClient.initialize();
            var result = givenMcpClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("get_test")
                    .arguments(Map.of())
                    .build());

            // Then
            then(result.isError()).isTrue();
            then(result.meta()).isNull();
        }
    }
}
