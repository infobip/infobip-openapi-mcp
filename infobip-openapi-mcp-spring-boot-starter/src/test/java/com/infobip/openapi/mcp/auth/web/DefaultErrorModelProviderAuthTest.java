package com.infobip.openapi.mcp.auth.web;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import java.util.List;
import org.json.JSONException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test-http")
@TestPropertySource(properties = {"infobip.openapi.mcp.security.auth.override-external-response=true"})
class DefaultErrorModelProviderAuthTest extends AuthenticationTestBase {

    @Autowired
    private ScopeDiscoveryService scopeDiscoveryService;

    @Autowired
    private OpenApiRegistry openApiRegistry;

    @Test
    void shouldReturnDefaultErrorModelForUnauthorizedStatus() throws JSONException {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .willReturn(aResponse()
                                .withStatus(401)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"custom_error\":\"Token expired\",\"error_code\":\"AUTH_001\"}")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer expired-token");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(
                """
                        {
                            "jsonrpc": "2.0",
                            "method": "tools/list",
                            "id": 1
                        }
                        """,
                headers);

        // when
        var response =
                restTemplate.exchange("http://localhost:" + port + "/mcp", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");
        then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNotEmpty();
        then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).hasSize(1);
        then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE).getFirst())
                .isEqualTo("Bearer resource_metadata=\"" + "http://localhost:"
                        + getStaticWireMockServer().port() + "/.well-known/oauth-protected-resource\"");

        // Should return DefaultErrorModelProvider response, not external auth service response
        String expectedJson =
                """
                        {
                            "error": "Unauthorized",
                            "description": "Authentication required. Please provide valid credentials."
                        }
                        """;
        JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.STRICT);
    }

    @Test
    void shouldReturnDefaultErrorModelForForbiddenStatus() throws JSONException {
        // given
        getStaticWireMockServer()
                .stubFor(
                        get(urlEqualTo("/auth/validate"))
                                .willReturn(
                                        aResponse()
                                                .withStatus(403)
                                                .withHeader("Content-Type", "application/xml")
                                                .withBody(
                                                        "<error><message>Insufficient permissions</message><code>PERM_DENIED</code></error>")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer limited-token");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(
                """
                        {
                            "jsonrpc": "2.0",
                            "method": "tools/list",
                            "id": 1
                        }
                        """,
                headers);

        // when
        var response =
                restTemplate.exchange("http://localhost:" + port + "/mcp", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");

        // Should return DefaultErrorModelProvider response in JSON format, not external XML
        String expectedJson =
                """
                        {
                            "error": "Forbidden",
                            "description": "Access denied. You don't have permission to access this resource."
                        }
                        """;
        JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.STRICT);
    }

    @Test
    void shouldReturnDefaultErrorModelForBadRequestFromAuthService() throws JSONException {
        // given
        getStaticWireMockServer()
                .stubFor(
                        get(urlEqualTo("/auth/validate"))
                                .willReturn(
                                        aResponse()
                                                .withStatus(400)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody(
                                                        "{\"validation_errors\":[\"Invalid token format\",\"Missing bearer prefix\"]}")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "malformed-token");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(
                """
                        {
                            "jsonrpc": "2.0",
                            "method": "tools/list",
                            "id": 1
                        }
                        """,
                headers);

        // when
        var response =
                restTemplate.exchange("http://localhost:" + port + "/mcp", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");

        // Should return DefaultErrorModelProvider response
        String expectedJson =
                """
                        {
                            "error": "Bad Request",
                            "description": "Check the request syntax and parameters and try again."
                        }
                        """;
        JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.STRICT);
    }

    @Test
    void shouldReturnDefaultErrorModelForInternalServerErrorFromAuthService() throws JSONException {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .willReturn(aResponse()
                                .withStatus(500)
                                .withHeader("Content-Type", "text/plain")
                                .withBody("Database connection failed")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer any-token");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(
                """
                        {
                            "jsonrpc": "2.0",
                            "method": "tools/list",
                            "id": 1
                        }
                        """,
                headers);

        // when
        var response =
                restTemplate.exchange("http://localhost:" + port + "/mcp", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");

        // Should return DefaultErrorModelProvider response, not external plain text
        String expectedJson =
                """
                        {
                            "error": "Internal Server Error",
                            "description": "An unexpected error occurred on the server."
                        }
                        """;
        JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.STRICT);
    }

    @Test
    void shouldReturnDefaultErrorModelForNotFoundFromAuthService() throws JSONException {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .willReturn(aResponse()
                                .withStatus(404)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"message\":\"Auth endpoint not found\",\"path\":\"/auth/validate\"}")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer test-token");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(
                """
                        {
                            "jsonrpc": "2.0",
                            "method": "tools/list",
                            "id": 1
                        }
                        """,
                headers);

        // when
        var response =
                restTemplate.exchange("http://localhost:" + port + "/mcp", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");

        // Should return DefaultErrorModelProvider response
        String expectedJson =
                """
                        {
                            "error": "Not Found",
                            "description": "The requested resource was not found."
                        }
                        """;
        JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.STRICT);
    }

    @Test
    void shouldEnsureResponseIsAlwaysJsonFormat() throws JSONException {
        // given - external service returns XML but we should get JSON
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .willReturn(aResponse()
                                .withStatus(403)
                                .withHeader("Content-Type", "application/xml")
                                .withBody("<?xml version=\"1.0\"?><error>Access forbidden</error>")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer invalid-token");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(
                """
                        {
                            "jsonrpc": "2.0",
                            "method": "tools/list",
                            "id": 1
                        }
                        """,
                headers);

        // when
        var response =
                restTemplate.exchange("http://localhost:" + port + "/mcp", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");

        // Should return JSON format from DefaultErrorModelProvider, not XML
        String expectedJson =
                """
                        {
                            "error": "Forbidden",
                            "description": "Access denied. You don't have permission to access this resource."
                        }
                        """;
        JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.STRICT);
    }

    @Test
    void shouldReturnWwwAuthenticateWithScopesForNoAuthorizationHeader() throws JSONException {
        // given - external service returns XML, but we should get JSON
        var givenOpenApiSpec =
                """
                {
                    "openapi": "3.1.0",
                    "info": {"title": "Test API", "version": "1.0.0"},
                    "paths": {
                        "/path1": {
                            "get": {
                                "security": [
                                    {
                                        "oauth-sample": ["read:resource1"]
                                    }
                                ]
                            },
                            "post": {
                                "security": [
                                    {
                                        "oauth-sample": [
                                            "write:resource1"
                                        ]
                                    }
                                ]
                            }
                        }
                    },
                    "components": {
                        "securitySchemes": {
                            "oauth-sample": {
                                "type": "oauth2"
                            }
                        }
                    }
                }
                """;
        setupCustomOpenAPIMock(getStaticWireMockServer(), givenOpenApiSpec);
        openApiRegistry.reload();
        scopeDiscoveryService.discover();

        var headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(
                """
                {
                    "jsonrpc": "2.0",
                    "method": "tools/list",
                    "id": 1
                }
                """,
                headers);

        // when
        var response =
                restTemplate.exchange("http://localhost:" + port + "/mcp", HttpMethod.POST, entity, String.class);

        // cleanup - restore original OpenAPI spec
        setupOpenAPIMock(getStaticWireMockServer());
        openApiRegistry.reload();
        scopeDiscoveryService.discover();

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");
        then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNotEmpty();
        then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).hasSize(1);
        then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE).getFirst())
                .contains("Bearer resource_metadata=\""
                        + "http://localhost:"
                        + getStaticWireMockServer().port()
                        + "/.well-known/oauth-protected-resource\""
                        + ", scope=\"write:resource1 read:resource1\"");

        // Should return JSON format from DefaultErrorModelProvider, not XML
        String expectedJson =
                """
                {
                    "error": "Unauthorized",
                    "description": "Authentication required. Please provide valid credentials."
                }
                """;
        JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.STRICT);
    }

    @Nested
    @TestPropertySource(properties = "infobip.openapi.mcp.security.auth.oauth.enabled=false")
    class OAuthDisabled extends AuthenticationTestBase {

        @Test
        void shouldNotReturnWwwAuthenticateForNoAuthorizationHeader() throws JSONException {
            // given - external service returns XML, but we should get JSON
            var headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
            headers.setContentType(MediaType.APPLICATION_JSON);
            var entity = new HttpEntity<>(
                    """
                            {
                                "jsonrpc": "2.0",
                                "method": "tools/list",
                                "id": 1
                            }
                            """,
                    headers);

            // when
            var baseUrl = "http://localhost:" + port + "/mcp";
            var response = restTemplate.exchange(baseUrl, HttpMethod.POST, entity, String.class);

            // then
            then(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");
            then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNullOrEmpty();

            // Should return JSON format from DefaultErrorModelProvider, not XML
            String expectedJson =
                    """
                            {
                                "error": "Unauthorized",
                                "description": "Authentication required. Please provide valid credentials."
                            }
                            """;
            JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.STRICT);
        }
    }

    @Nested
    @TestPropertySource(
            properties = {
                "infobip.openapi.mcp.security.auth.oauth.www-authenticate.include-mcp-endpoint=true",
                "infobip.openapi.mcp.security.auth.oauth.www-authenticate.url-source=x_forwarded_host",
                "spring.ai.mcp.server.streamable-http.mcp-endpoint=/test"
            })
    class WwwAuthenticateHeaderCustomization extends AuthenticationTestBase {

        @Test
        void shouldReturnXForwardedHostHeader() throws JSONException {
            // given
            var headers = new HttpHeaders();
            headers.set("X-Forwarded-Host", "custom.host.com");
            headers.set("X-Forwarded-Proto", "https");
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
            headers.setContentType(MediaType.APPLICATION_JSON);
            var entity = new HttpEntity<>(
                    """
                            {
                                "jsonrpc": "2.0",
                                "method": "tools/list",
                                "id": 1
                            }
                            """,
                    headers);

            // when
            var response =
                    restTemplate.exchange("http://localhost:" + port + "/test", HttpMethod.POST, entity, String.class);
            // then
            then(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");
            then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNotEmpty();
            then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).hasSize(1);
            then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE).getFirst())
                    .isEqualTo("Bearer resource_metadata=\"" + "https://custom.host.com/test"
                            + "/.well-known/oauth-protected-resource\"");

            String expectedJson =
                    """
                            {
                                "error": "Unauthorized",
                                "description": "Authentication required. Please provide valid credentials."
                                }
                            """;
            JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.STRICT);
        }

        @Test
        void shouldReturnApiBaseUrlWhenHeadersNotPresent() throws JSONException {
            // given
            var headers = new HttpHeaders();
            headers.set(
                    HttpHeaders.HOST, "localhost:" + getStaticWireMockServer().port());
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
            headers.setContentType(MediaType.APPLICATION_JSON);
            var entity = new HttpEntity<>(
                    """
                            {
                                "jsonrpc": "2.0",
                                "method": "tools/list",
                                "id": 1
                            }
                            """,
                    headers);

            // when
            var response =
                    restTemplate.exchange("http://localhost:" + port + "/test", HttpMethod.POST, entity, String.class);
            // then
            then(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");
            then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNotEmpty();
            then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).hasSize(1);
            then(response.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE).getFirst())
                    .isEqualTo("Bearer resource_metadata=\"" + "http://localhost:" + port + "/test"
                            + "/.well-known/oauth-protected-resource\"");

            String expectedJson =
                    """
                            {
                                "error": "Unauthorized",
                                "description": "Authentication required. Please provide valid credentials."
                                }
                            """;
            JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.STRICT);
        }
    }
}
