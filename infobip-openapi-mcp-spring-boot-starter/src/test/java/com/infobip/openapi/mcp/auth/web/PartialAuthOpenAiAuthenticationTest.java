package com.infobip.openapi.mcp.auth.web;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end contract of {@code PARTIAL_AUTH_OPENAI_COMPATIBLE} authentication mode on the STATELESS transport: like
 * {@code PARTIAL_AUTH_CLAUDE_COMPATIBLE}, requests reach the MCP transport without credential validation (the configured
 * {@code auth-url} must never be called), but an API authorization failure is re-signaled <em>in-band</em>:
 * the transport answers {@code 200} with an {@code isError} tool result whose {@code _meta} carries the
 * challenge under {@code mcp/www_authenticate}, and no HTTP challenge is produced. Because the test spec
 * declares no security, every tool advertises the anonymous {@code securitySchemes} option, while the
 * login path stays available through the in-band challenge.
 */
@ActiveProfiles("test-partial-auth-openai")
class PartialAuthOpenAiAuthenticationTest extends AuthenticationTestBase {

    private static final String TOOLS_LIST_REQUEST = """
            {"jsonrpc": "2.0", "method": "tools/list", "id": 1}""";

    private static final String TOOL_CALL_REQUEST = """
            {"jsonrpc": "2.0", "method": "tools/call", "id": 2, "params": {"name": "get_users", "arguments": {}}}""";

    /**
     * The challenge as it appears inside the JSON response body, i.e. with its RFC 6750 parameter
     * quotes escaped. No {@code scope} segment: the test spec discovers no scopes.
     */
    private static final String CHALLENGE_ERROR_PARAMS =
            "error=\\\"insufficient_scope\\\", error_description=\\\"You need to login to continue\\\"";

    private static final String META_CHALLENGE_ARRAY_OPEN = "\"mcp/www_authenticate\":[\"Bearer resource_metadata=";

    @Test
    void shouldServeToolsListWithSecuritySchemesWithoutCredentials() {
        // given
        var entity = jsonRpcEntity(TOOLS_LIST_REQUEST, null);

        // when
        var response = restTemplate.exchange(mcpUrl(), HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(response.getBody()).contains("get_users");
        then(response.getBody()).contains("\"securitySchemes\":[{\"type\":\"noauth\"}]");
        verifyNoAuthUrlCalls();
    }

    @Test
    void shouldAnnotateApiUnauthorizedResultInBandWithoutHttpChallenge() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/users"))
                        .willReturn(aResponse()
                                .withStatus(401)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"apiError\":\"denied\"}")));
        var entity = jsonRpcEntity(TOOL_CALL_REQUEST, null);

        // when
        var response = restTemplate.exchange(mcpUrl(), HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        then(response.getBody()).contains("\"isError\":true");
        then(response.getBody()).contains(META_CHALLENGE_ARRAY_OPEN);
        then(response.getBody()).contains(CHALLENGE_ERROR_PARAMS);
        then(response.getBody()).contains("apiError").contains("denied");
        verifyNoAuthUrlCalls();
    }

    @Test
    void shouldAnnotateApiForbiddenWithTheSameChallengeAsUnauthorized() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/users"))
                        .willReturn(aResponse()
                                .withStatus(403)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"apiError\":\"insufficient\"}")));
        var entity = jsonRpcEntity(TOOL_CALL_REQUEST, null);

        // when
        var response = restTemplate.exchange(mcpUrl(), HttpMethod.POST, entity, String.class);

        // then — same in-band signal: both statuses mean "login is required to continue"
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        then(response.getBody()).contains("\"isError\":true");
        then(response.getBody()).contains(META_CHALLENGE_ARRAY_OPEN);
        then(response.getBody()).contains(CHALLENGE_ERROR_PARAMS);
        then(response.getBody()).contains("apiError").contains("insufficient");
        verifyNoAuthUrlCalls();
    }

    @Test
    void shouldNotAnnotateSuccessfulToolCall() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/users"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"data\":\"public\"}")));
        var entity = jsonRpcEntity(TOOL_CALL_REQUEST, null);

        // when
        var response = restTemplate.exchange(mcpUrl(), HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(response.getBody()).contains("\"isError\":false");
        then(response.getBody()).contains("public");
        then(response.getBody()).doesNotContain("mcp/www_authenticate");
        verifyNoAuthUrlCalls();
    }

    @Test
    void shouldNotAnnotateNonAuthorizationApiError() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/users"))
                        .willReturn(aResponse()
                                .withStatus(500)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"apiError\":\"boom\"}")));
        var entity = jsonRpcEntity(TOOL_CALL_REQUEST, null);

        // when
        var response = restTemplate.exchange(mcpUrl(), HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(response.getBody()).contains("\"isError\":true");
        then(response.getBody()).contains("apiError").contains("boom");
        then(response.getBody()).doesNotContain("mcp/www_authenticate");
        then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        verifyNoAuthUrlCalls();
    }

    private String mcpUrl() {
        return "http://localhost:" + port + "/mcp";
    }

    private HttpEntity<String> jsonRpcEntity(String body, String authorizationHeaderValue) {
        var headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorizationHeaderValue != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeaderValue);
        }
        return new HttpEntity<>(body, headers);
    }

    private void verifyNoAuthUrlCalls() {
        getStaticWireMockServer().verify(0, getRequestedFor(urlEqualTo("/auth/validate")));
    }
}
