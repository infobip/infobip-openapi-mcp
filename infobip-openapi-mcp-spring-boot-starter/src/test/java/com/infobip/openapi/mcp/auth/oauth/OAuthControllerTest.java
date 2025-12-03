package com.infobip.openapi.mcp.auth.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import org.json.JSONException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test-http")
@TestPropertySource(properties = "infobip.openapi.mcp.security.auth.oauth.scope-discovery.scope-extensions=x-scopes")
public class OAuthControllerTest extends OAuthTestBase {

    @Autowired
    private ScopeDiscoveryService scopeDiscoveryService;

    @ParameterizedTest
    @ValueSource(strings = {"/.well-known/oauth-authorization-server", "/.well-known/openid-configuration"})
    void shouldTimeoutWhenAuthServiceIsSlow(String givenWellKnownEndpoint) {
        // Given - This tests READ TIMEOUT
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo(givenWellKnownEndpoint))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withFixedDelay(1500))); // 1.5 seconds delay, readTimeout is 1 seconds

        // When
        var response = restTemplate.exchange(
                "http://localhost:" + port + givenWellKnownEndpoint, HttpMethod.GET, null, String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/.well-known/oauth-authorization-server", "/.well-known/openid-configuration"})
    void shouldApplyOnlyXFFEnricherToWellKnownEndpoint(String givenWellKnownEndpoint) {
        // Given
        var givenXForwardedFor = "www.example.com";
        var givenXForwardedHost = "forwarded.example.com";
        var givenResponseBody = """
                {
                  "issuer": "https://auth-server.example.com",
                  "authorization_endpoint": "https://auth-server.example.com/auth",
                  "token_endpoint": "https://auth-server.example.com/token"
                }
                """;

        getStaticWireMockServer()
                .stubFor(get(urlEqualTo(givenWellKnownEndpoint))
                        .withHeader("X-Forwarded-For", equalTo(givenXForwardedFor + ", 127.0.0.1"))
                        .willReturn(aResponse().withStatus(200).withBody(givenResponseBody)));

        // When
        var headers = new HttpHeaders();
        headers.set("X-Forwarded-For", givenXForwardedFor);
        headers.set("X-Forwarded-Host", givenXForwardedHost);
        var requestEntity = new HttpEntity<>(headers);
        var response = restTemplate.exchange(
                "http://localhost:" + port + givenWellKnownEndpoint, HttpMethod.GET, requestEntity, String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");
        System.out.println(response.getHeaders().get("X-Forwarded-For"));

        getStaticWireMockServer()
                .verify(getRequestedFor(urlEqualTo(givenWellKnownEndpoint))
                        .withHeader("X-Forwarded-For", equalTo(givenXForwardedFor + ", 127.0.0.1"))
                        .withoutHeader("X-Forwarded-Host"));
    }

    @Test
    void shouldUseResolvedScopes() throws JSONException {
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

        scopeDiscoveryService.discover();

        // When
        var response = restTemplate.exchange(
                "http://localhost:" + port + givenWellKnownEndpoint, HttpMethod.GET, null, String.class);

        // Then
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");

        var expectedJson = """
                {
                  "issuer": "http://auth-server",
                  "authorization_endpoint": "http://auth-server/auth",
                  "token_endpoint": "http://auth-server/token",
                  "scopes_supported": ["scope1"]
                }
                """;
        JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.STRICT);
    }

    @Nested
    @TestPropertySource(properties = "infobip.openapi.mcp.security.auth.oauth.scope-discovery.enabled=false")
    class ScopeDiscoveryDisabled extends OAuthTestBase {

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
            JSONAssert.assertEquals(givenResponseBody, response.getBody(), JSONCompareMode.STRICT);
        }
    }

    @Test
    void shouldReturnProtectedResource() throws JSONException {
        // Given
        var givenOpenApi = """
                {
                    "openapi": "3.1.0",
                    "info": {"title": "User management", "version": "1.0.0"},
                    "paths": {
                        "/users": {
                            "get": {},
                            "post": {}
                        }
                    }
                }
                """;
        reloadOpenApi(givenOpenApi);
        scopeDiscoveryService.discover();
        var givenWellKnownEndpoint = "/.well-known/oauth-protected-resource";

        // When
        var response = restTemplate.exchange(
                "http://localhost:" + port + givenWellKnownEndpoint, HttpMethod.GET, null, String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");

        var expectedJson = """
                {
                  "resource": "http://localhost:%d/mcp",
                  "resource_name": "User management",
                  "authorization_servers": [
                    "http://localhost:%d"
                  ],
                  "bearer_methods_supported": [
                    "access_token"
                  ]
                }
                """.formatted(port, staticWireMockServer.port());
        JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void shouldReturnProtectedResourceWithScopes() throws JSONException {
        // Given
        var givenOpenApiWithScopes = """
                {
                    "openapi": "3.1.0",
                    "info": {"title": "User management", "version": "1.0.0"},
                    "paths": {
                        "/users": {
                            "get": {
                                "x-scopes": ["user:read"]
                            },
                            "post": {
                                "x-scopes": ["user:write"]
                            }
                        }
                    }
                }
                """;
        reloadOpenApi(givenOpenApiWithScopes);
        scopeDiscoveryService.discover();
        var givenWellKnownEndpoint = "/.well-known/oauth-protected-resource";

        // When
        var response = restTemplate.exchange(
                "http://localhost:" + port + givenWellKnownEndpoint, HttpMethod.GET, null, String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");

        var expectedJson = """
                {
                  "resource": "http://localhost:%d/mcp",
                  "resource_name": "User management",
                  "authorization_servers": [
                    "http://localhost:%d"
                  ],
                  "bearer_methods_supported": [
                    "access_token"
                  ],
                  "scopes_supported": [
                    "user:read",
                    "user:write"
                  ]
                }
                """.formatted(port, staticWireMockServer.port());
        JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
