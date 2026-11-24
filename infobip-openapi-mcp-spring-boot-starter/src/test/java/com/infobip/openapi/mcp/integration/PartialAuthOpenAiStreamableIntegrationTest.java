package com.infobip.openapi.mcp.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.integration.base.PartialAuthIntegrationTestBase;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves the design claim that {@code PARTIAL_AUTH_OPENAI_COMPATIBLE} is transport-agnostic: its signal rides on the
 * MCP result, not on the HTTP response plane, so the STREAMABLE protocol — where {@code PARTIAL_AUTH_CLAUDE_COMPATIBLE}
 * cannot remap at all (see {@link PartialAuthClaudeStreamablePassthroughTest}) — carries the full mixed-auth
 * contract as well.
 */
@ActiveProfiles({"integration", "integration-security"})
@TestPropertySource(
        properties = {
            "infobip.openapi.mcp.security.auth.mode=PARTIAL_AUTH_OPENAI_COMPATIBLE",
            "infobip.openapi.mcp.security.auth.oauth.www-authenticate.url-source=api_base_url",
            "infobip.openapi.mcp.security.auth.oauth.www-authenticate.include-mcp-endpoint=false",
            "spring.ai.mcp.server.protocol = streamable"
        })
class PartialAuthOpenAiStreamableIntegrationTest extends PartialAuthIntegrationTestBase {

    @Test
    void shouldCarryInBandChallengeOnStreamableProtocol() {
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

            // Then — the contrast case of the passthrough test now carries the login trigger
            then(result.isError()).isTrue();
            then(result.meta()).containsKey("mcp/www_authenticate");
            var challenge = (String) ((List<?>) result.meta().get("mcp/www_authenticate")).getFirst();
            then(challenge)
                    .startsWith("Bearer resource_metadata=\"")
                    .contains("error=\"insufficient_scope\"")
                    .contains("error_description=\"You need to login to continue\"");
        }
    }
}
