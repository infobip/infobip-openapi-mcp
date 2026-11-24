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
 * On the STREAMABLE protocol no HTTP challenge can be produced, so a hint routing this client to the HTTP
 * plane (here: the JDK {@code User-Agent} the test client sends) must fall back to the in-band plane instead
 * of losing the login trigger — the behavior the mode's startup warning announces.
 */
@ActiveProfiles({"integration", "integration-security"})
@TestPropertySource(
        properties = {
            "infobip.openapi.mcp.security.auth.mode=PARTIAL_AUTH_AUTODETECT",
            "infobip.openapi.mcp.security.auth.client-hints[0].user-agent-pattern=^java",
            "infobip.openapi.mcp.security.auth.client-hints[0].plane=HTTP",
            "infobip.openapi.mcp.security.auth.oauth.www-authenticate.url-source=api_base_url",
            "infobip.openapi.mcp.security.auth.oauth.www-authenticate.include-mcp-endpoint=false",
            "spring.ai.mcp.server.protocol = streamable"
        })
class PartialAuthAutodetectStreamableFallbackTest extends PartialAuthIntegrationTestBase {

    @Test
    void shouldFallBackToInMetaPlaneWhenHttpPlaneRequested() {
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

            // Then — HTTP plane requested by the hint, in-band delivered
            then(result.isError()).isTrue();
            then(result.meta()).containsKey("mcp/www_authenticate");
            var challenge = (String) ((List<?>) result.meta().get("mcp/www_authenticate")).getFirst();
            then(challenge).startsWith("Bearer resource_metadata=\"");
        }
    }
}
