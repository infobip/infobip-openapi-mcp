package com.infobip.openapi.mcp.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.integration.base.PartialAuthIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Configured {@code client-hints} take precedence over the built-in known-client table: the OpenAI-styled
 * client is rerouted to the HTTP challenge plane by configuration, while an unmatched client still lands on
 * the default in-band plane.
 */
@ActiveProfiles({"integration", "integration-security"})
@TestPropertySource(
        properties = {
            "infobip.openapi.mcp.security.auth.mode=PARTIAL_AUTH_AUTODETECT",
            "infobip.openapi.mcp.security.auth.client-hints[0].user-agent-pattern=openai-mcp",
            "infobip.openapi.mcp.security.auth.client-hints[0].plane=HTTP",
            "infobip.openapi.mcp.security.auth.default-challenge-plane=IN_META",
            "infobip.openapi.mcp.security.auth.oauth.www-authenticate.url-source=api_base_url",
            "infobip.openapi.mcp.security.auth.oauth.www-authenticate.include-mcp-endpoint=false",
            "spring.ai.mcp.server.protocol = stateless"
        })
class PartialAuthAutodetectClientHintsIntegrationTest extends PartialAuthIntegrationTestBase {

    @Test
    void shouldLetConfiguredHintRouteOpenAiStyledClientToHttpPlane() {
        given401ReturningTool();

        var response = postJsonRpcAs("openai-mcp/1.0.0 (Responses API)", toolsCallBody("get_test"));

        then(response.statusCode()).isEqualTo(401);
        then(response.headers().firstValue("WWW-Authenticate").orElseThrow()).startsWith("Bearer resource_metadata=\"");
    }

    @Test
    void shouldKeepUnmatchedClientOnDefaultInMetaPlane() {
        given401ReturningTool();

        var response = postJsonRpcAs("SomeClient/1.0", toolsCallBody("get_test"));

        then(response.statusCode()).isEqualTo(200);
        then(response.body()).contains("\"isError\":true").contains("mcp/www_authenticate");
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
