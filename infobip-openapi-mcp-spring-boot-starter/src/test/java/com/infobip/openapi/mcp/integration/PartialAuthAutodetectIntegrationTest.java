package com.infobip.openapi.mcp.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.integration.base.PartialAuthIntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code PARTIAL_AUTH_AUTODETECT} on the stateless transport with only the built-in known-client hints:
 * a Claude-styled {@code User-Agent} gets the HTTP {@code 401} challenge, an OpenAI-styled one the in-band
 * {@code _meta} challenge on a successful HTTP response, and an unmatched client the configured default
 * (in-band). Tools are advertised with {@code _meta.securitySchemes} as in
 * {@code PARTIAL_AUTH_OPENAI_COMPATIBLE}, so no host gates the tools before the API has had its say.
 */
@ActiveProfiles({"integration", "integration-security"})
@TestPropertySource(
        properties = {
            "infobip.openapi.mcp.security.auth.mode=PARTIAL_AUTH_AUTODETECT",
            "infobip.openapi.mcp.security.auth.oauth.www-authenticate.url-source=api_base_url",
            "infobip.openapi.mcp.security.auth.oauth.www-authenticate.include-mcp-endpoint=false",
            "spring.ai.mcp.server.protocol = stateless"
        })
class PartialAuthAutodetectIntegrationTest extends PartialAuthIntegrationTestBase {

    @Test
    void shouldRemapHttpChallengeForClaudeStyledClient() {
        given401ReturningTool();

        var response = postJsonRpcAs("claude-code/2.1.311 (cli)", toolsCallBody("get_test"));

        then(response.statusCode()).isEqualTo(401);
        then(response.headers().firstValue("WWW-Authenticate").orElseThrow())
                .startsWith("Bearer resource_metadata=\"")
                .contains("scope=\"required_scope\"");
    }

    @Test
    void shouldCarryInMetaChallengeForOpenAiStyledClient() {
        given401ReturningTool();

        var response = postJsonRpcAs("openai-mcp/1.0.0 (Responses API)", toolsCallBody("get_test"));

        then(response.statusCode()).isEqualTo(200);
        then(response.body()).contains("\"isError\":true").contains("mcp/www_authenticate");
    }

    @Test
    void shouldCarryInMetaChallengeForCodexStyledClient() {
        given401ReturningTool();

        var response = postJsonRpcAs("codex-mcp-client/0.171.0-alpha.13", toolsCallBody("get_test"));

        then(response.statusCode()).isEqualTo(200);
        then(response.body()).contains("\"isError\":true").contains("mcp/www_authenticate");
    }

    @Test
    void shouldDefaultUnmatchedClientToInMetaChallenge() {
        given401ReturningTool();

        var response = postJsonRpcAs("Cursor/1.0.0", toolsCallBody("get_test"));

        then(response.statusCode()).isEqualTo(200);
        then(response.body()).contains("\"isError\":true").contains("mcp/www_authenticate");
    }

    @Test
    void shouldAdvertiseSecuritySchemesOnEveryTool() {
        givenOpenAPISpecification("/openapi/base.json");

        try (var givenMcpClient = givenMcpClientWithAuthHeader("")) {
            givenMcpClient.initialize();
            var tools = givenMcpClient.listTools().tools();

            then(tools).isNotEmpty();
            @SuppressWarnings("unchecked")
            var securitySchemes =
                    (List<Map<String, Object>>) tools.getFirst().meta().get("securitySchemes");
            then(securitySchemes.getFirst()).containsEntry("type", "noauth");
            then(securitySchemes.get(1))
                    .containsEntry("type", "oauth2")
                    .containsEntry("scopes", List.of("required_scope"));
        }
    }

    private void given401ReturningTool() {
        givenOpenAPISpecification("/openapi/base.json");
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/test"))
                        .willReturn(aResponse()
                                .withStatus(401)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"apiError\":\"denied\"}")));
    }
}
