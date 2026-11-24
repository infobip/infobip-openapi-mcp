package com.infobip.openapi.mcp.auth.web;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end contract of {@code PARTIAL_AUTH_CLAUDE_COMPATIBLE} authentication mode on the STATELESS transport: requests
 * reach the MCP transport without credential validation (the configured {@code auth-url} must never be
 * called) and only tool calls the backing API answered with {@code 401}/{@code 403} come back as HTTP
 * authentication challenges in the exact format of the {@code ALL_OR_NOTHING} challenges.
 */
@ActiveProfiles("test-partial-auth-claude")
class PartialAuthClaudeAuthenticationFilterTest extends AuthenticationTestBase {

    private static final String TOOLS_LIST_REQUEST = """
            {"jsonrpc": "2.0", "method": "tools/list", "id": 1}""";

    private static final String TOOL_CALL_REQUEST = """
            {"jsonrpc": "2.0", "method": "tools/call", "id": 2, "params": {"name": "get_users", "arguments": {}}}""";

    private static final String UNAUTHORIZED_ERROR_MODEL_BODY =
            "{\"error\":\"Unauthorized\",\"description\":\"Authentication required. Please provide valid credentials.\"}";

    private static final String FORBIDDEN_ERROR_MODEL_BODY =
            "{\"error\":\"Forbidden\",\"description\":\"Access denied. You don't have permission to access this resource.\"}";

    @Test
    void shouldServeToolsListWithoutCredentials() {
        // given
        var entity = jsonRpcEntity(TOOLS_LIST_REQUEST, null);

        // when
        var response = restTemplate.exchange(mcpUrl(), HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(response.getBody()).contains("get_users");
        verifyNoAuthUrlCalls();
    }

    @Test
    void shouldCallApiWithoutCredentialsWhenApiAllowsIt() {
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
        verifyNoAuthUrlCalls();
    }

    @Test
    void shouldRemapApiUnauthorizedIntoHttpChallenge() {
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
        then(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        then(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer resource_metadata=");
        then(response.getHeaders().getContentType().toString()).contains("application/json");
        then(response.getBody()).isEqualTo(UNAUTHORIZED_ERROR_MODEL_BODY);
        verifyNoAuthUrlCalls();
    }

    @Test
    void shouldRemapApiForbiddenIntoScopeErrorChallenge() {
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

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        then(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("error=\"insufficient_scope\"");
        then(response.getBody()).isEqualTo(FORBIDDEN_ERROR_MODEL_BODY);
        verifyNoAuthUrlCalls();
    }

    @Test
    void shouldForwardAuthorizationHeaderToApiWithoutValidatingIt() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/users"))
                        .withHeader("Authorization", equalTo("Bearer api-native-token"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"data\":\"authorized\"}")));
        var entity = jsonRpcEntity(TOOL_CALL_REQUEST, "Bearer api-native-token");

        // when
        var response = restTemplate.exchange(mcpUrl(), HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(response.getBody()).contains("authorized");
        getStaticWireMockServer()
                .verify(getRequestedFor(urlEqualTo("/users"))
                        .withHeader("Authorization", equalTo("Bearer api-native-token")));
        verifyNoAuthUrlCalls();
    }

    @Test
    void shouldLeaveNonAuthorizationApiErrorsAsMcpLevelErrors() {
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
