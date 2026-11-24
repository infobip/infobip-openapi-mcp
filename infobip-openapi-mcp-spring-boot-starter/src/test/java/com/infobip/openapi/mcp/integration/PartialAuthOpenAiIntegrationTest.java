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
 * The {@code PARTIAL_AUTH_OPENAI_COMPATIBLE} contract through a real MCP client on the stateless transport: an
 * anonymous client lists tools whose {@code _meta} advertises per-operation {@code securitySchemes}
 * resolved from the specification's own security declarations, and a tool call the backing API
 * answered with {@code 401} stays an MCP-level {@code isError} result — no HTTP challenge anywhere —
 * while {@code _meta} carries the in-band {@code mcp/www_authenticate} challenge.
 */
@ActiveProfiles({"integration", "integration-security"})
@TestPropertySource(
        properties = {
            "infobip.openapi.mcp.security.auth.mode=PARTIAL_AUTH_OPENAI_COMPATIBLE",
            "infobip.openapi.mcp.security.auth.oauth.www-authenticate.url-source=api_base_url",
            "infobip.openapi.mcp.security.auth.oauth.www-authenticate.include-mcp-endpoint=false",
            "spring.ai.mcp.server.protocol = stateless"
        })
class PartialAuthOpenAiIntegrationTest extends PartialAuthIntegrationTestBase {

    @Test
    void shouldAdvertiseSecuritySchemesOnEveryTool() {
        // Given
        givenOpenAPISpecification("/openapi/base.json");

        try (var givenMcpClient = givenMcpClientWithAuthHeader("")) {
            // When
            givenMcpClient.initialize();
            var tools = givenMcpClient.listTools().tools();

            // Then
            then(tools).isNotEmpty();
            var securitySchemes = securitySchemesOf(tools.getFirst());
            then(securitySchemes.getFirst()).containsEntry("type", "noauth");
            then(securitySchemes.get(1))
                    .containsEntry("type", "oauth2")
                    .containsEntry("scopes", List.of("required_scope"));
        }
    }

    @Test
    void shouldAdvertiseSecuritySchemesPerOperation() {
        // Given
        givenOpenAPISpecification("/openapi/meta-security-schemes.json");

        try (var givenMcpClient = givenMcpClientWithAuthHeader("")) {
            // When
            givenMcpClient.initialize();
            var tools = givenMcpClient.listTools().tools();

            // Then — inherited global security is OAuth2-only, the operation's own scopes replace the
            // global ones, and an empty requirement offers the anonymous option alongside the mandatory
            // scopes every token must carry.
            then(securitySchemesOf(toolNamed(tools, "get_inherited")))
                    .isEqualTo(List.of(Map.of("type", "oauth2", "scopes", List.of("global_scope", "required_scope"))));
            then(securitySchemesOf(toolNamed(tools, "get_narrow")))
                    .isEqualTo(List.of(Map.of("type", "oauth2", "scopes", List.of("narrow_scope", "required_scope"))));
            then(securitySchemesOf(toolNamed(tools, "get_anonymous")))
                    .isEqualTo(List.of(
                            Map.of("type", "noauth"), Map.of("type", "oauth2", "scopes", List.of("required_scope"))));
        }
    }

    @Test
    void shouldCarryInBandChallengeOnApiUnauthorizedToolCall() {
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
            then(((McpSchema.TextContent) result.content().getFirst()).text()).isEqualTo("{\"apiError\":\"denied\"}");
            then(result.meta()).containsKey("mcp/www_authenticate");
            var challenges = result.meta().get("mcp/www_authenticate");
            then(challenges).isInstanceOf(List.class);
            var challenge = (String) ((List<?>) challenges).getFirst();
            then(challenge)
                    .startsWith("Bearer resource_metadata=\"")
                    .contains("scope=\"required_scope\"")
                    .contains("error=\"insufficient_scope\"")
                    .contains("error_description=\"You need to login to continue\"");
        }
    }

    @Test
    void shouldNotAnnotateSuccessfulToolCall() {
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
            then(result.meta()).isNull();
        }
    }

    private McpSchema.Tool toolNamed(List<McpSchema.Tool> tools, String name) {
        return tools.stream()
                .filter(tool -> tool.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> securitySchemesOf(McpSchema.Tool tool) {
        then(tool.meta()).isNotNull();
        return (List<Map<String, Object>>) tool.meta().get("securitySchemes");
    }
}
