package com.infobip.openapi.mcp.auth.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies that when scope discovery is disabled, the well-known endpoint response is passed through unchanged with all
 * advertised scopes. This lives in its own top-level class rather than as a nested class of {@link OAuthControllerTest}
 * so that it does not inherit that class's required {@code ScopeDiscoveryService} injection, which is absent when scope
 * discovery is disabled.
 */
@ActiveProfiles("test-http")
@TestPropertySource(
        properties = {
            "infobip.openapi.mcp.security.auth.oauth.scope-discovery.scope-extensions=x-scopes",
            "infobip.openapi.mcp.security.auth.oauth.scope-discovery.enabled=false"
        })
class OAuthControllerScopeDiscoveryDisabledTest extends OAuthTestBase {

    @Test
    void shouldUseAllScopes() throws JSONException {
        // Given
        var givenOpenApiScopes = """
                {
                    "openapi": "3.1.0",
                    "info": {"title": "Test API", "version": "1.0.0"},
                    "paths": {
                        "/path1": {
                            "get": {
                                "x-scopes": ["scope1", "scope2"]
                            },
                            "post": {
                                "x-scopes": "scope1"
                            }
                        }
                    }
                }
                """;
        var givenResponseBody = """
                {
                  "issuer": "http://auth-server",
                  "authorization_endpoint": "http://auth-server/auth",
                  "token_endpoint": "http://auth-server/token",
                  "scopes_supported": ["scope1, scope2", "scope3"]
                }
                """;

        var givenWellKnownEndpoint = "/.well-known/oauth-authorization-server";

        reloadOpenApi(givenOpenApiScopes);
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo(givenWellKnownEndpoint))
                        .willReturn(aResponse().withStatus(200).withBody(givenResponseBody)));

        // When
        var response = restTemplate.exchange(
                "http://localhost:" + port + givenWellKnownEndpoint, HttpMethod.GET, null, String.class);

        // Then
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");
        JSONAssert.assertEquals(givenResponseBody, response.getBody(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
