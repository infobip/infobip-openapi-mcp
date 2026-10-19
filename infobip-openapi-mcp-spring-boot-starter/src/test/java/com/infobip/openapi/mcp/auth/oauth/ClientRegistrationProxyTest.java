package com.infobip.openapi.mcp.auth.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import com.github.tomakehurst.wiremock.http.Fault;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test-http")
@DirtiesContext
@TestPropertySource(
        properties = {
            "infobip.openapi.mcp.security.auth.oauth.scope-discovery.scope-extensions=x-scopes",
            "infobip.openapi.mcp.security.auth.oauth.registration-proxy.enabled=true"
        })
public class ClientRegistrationProxyTest extends OAuthTestBase {

    private static final String OPENID_CONFIGURATION = "/.well-known/openid-configuration";
    private static final String UPSTREAM_REGISTRATION_PATH = "/clients-registrations/openid-connect";
    private static final String GIVEN_OPEN_API = """
            {
                "openapi": "3.1.0",
                "info": {"title": "User management", "version": "1.0.0"},
                "paths": {
                    "/users": {
                        "get": {"x-scopes": ["user:read"]},
                        "post": {"x-scopes": ["user:write"]}
                    }
                }
            }
            """;

    @Autowired
    private ScopeDiscoveryService scopeDiscoveryService;

    @BeforeEach
    void setUp() {
        getStaticWireMockServer().resetAll();
        reloadOpenApi(GIVEN_OPEN_API);
        scopeDiscoveryService.discover();
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo(OPENID_CONFIGURATION)).willReturn(okJson(authorizationServerMetadata())));
    }

    @Test
    void shouldReplaceScopeAndForwardRegistration() throws JSONException {
        // Given
        var givenRegistrationResponse = """
                {"client_id": "abc", "scope": "user:read user:write"}
                """;
        getStaticWireMockServer()
                .stubFor(post(urlEqualTo(UPSTREAM_REGISTRATION_PATH))
                        .willReturn(aResponse()
                                .withStatus(201)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("Cache-Control", "no-store")
                                .withBody(givenRegistrationResponse)));
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer initial-access-token");
        headers.set("X-Forwarded-For", "203.0.113.7");
        var givenRequest = """
                {"client_name": "Test client", "redirect_uris": ["http://localhost:3000/cb"], "scope": "openid"}
                """;

        // When
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/oauth/register",
                HttpMethod.POST,
                new HttpEntity<>(givenRequest, headers),
                String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        then(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        JSONAssert.assertEquals(givenRegistrationResponse, response.getBody(), JSONCompareMode.STRICT);

        var forwardedRequests = getStaticWireMockServer()
                .findAll(postRequestedFor(urlEqualTo(UPSTREAM_REGISTRATION_PATH))
                        .withHeader("Authorization", equalTo("Bearer initial-access-token"))
                        .withHeader("X-Forwarded-For", equalTo("203.0.113.7, 127.0.0.1")));
        then(forwardedRequests).hasSize(1);
        var forwardedScope = new JSONObject(forwardedRequests.getFirst().getBodyAsString()).getString("scope");
        then(forwardedScope.split(" ")).containsExactlyInAnyOrder("user:read", "user:write");
        JSONAssert.assertEquals("""
                {"client_name": "Test client", "redirect_uris": ["http://localhost:3000/cb"]}
                """, forwardedRequests.getFirst().getBodyAsString(), JSONCompareMode.LENIENT);
    }

    @Test
    void shouldAddScopeWhenClientOmitsIt() throws JSONException {
        // Given
        getStaticWireMockServer()
                .stubFor(post(urlEqualTo(UPSTREAM_REGISTRATION_PATH))
                        .willReturn(aResponse().withStatus(201).withBody("{}")));

        // When
        var response = postRegistration("""
                {"redirect_uris": ["http://localhost:3000/cb"]}
                """);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var forwardedBody = getStaticWireMockServer()
                .findAll(postRequestedFor(urlEqualTo(UPSTREAM_REGISTRATION_PATH)))
                .getFirst()
                .getBodyAsString();
        then(new JSONObject(forwardedBody).getString("scope").split(" "))
                .containsExactlyInAnyOrder("user:read", "user:write");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403})
    void shouldPassThroughUpstreamErrors(int givenStatus) throws JSONException {
        // Given
        var givenError = """
                {"error": "invalid_client_metadata", "error_description": "Policy rejected"}
                """;
        getStaticWireMockServer()
                .stubFor(post(urlEqualTo(UPSTREAM_REGISTRATION_PATH))
                        .willReturn(aResponse()
                                .withStatus(givenStatus)
                                .withHeader("Content-Type", "application/json")
                                .withBody(givenError)));

        // When
        var response = postRegistration("""
                {"redirect_uris": ["http://localhost:3000/cb"]}
                """);

        // Then
        then(response.getStatusCode().value()).isEqualTo(givenStatus);
        then(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        JSONAssert.assertEquals(givenError, response.getBody(), JSONCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[1, 2]", ""})
    void shouldRejectBodyThatIsNotJsonObject(String givenBody) throws JSONException {
        // When
        var response = postRegistration(givenBody);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JSONAssert.assertEquals("""
                {"error": "invalid_client_metadata"}
                """, response.getBody(), JSONCompareMode.LENIENT);
        getStaticWireMockServer().verify(0, postRequestedFor(urlEqualTo(UPSTREAM_REGISTRATION_PATH)));
    }

    @Test
    void shouldReturnBadGatewayWhenUpstreamIsUnreachable() {
        // Given
        getStaticWireMockServer()
                .stubFor(post(urlEqualTo(UPSTREAM_REGISTRATION_PATH))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        // When
        var response = postRegistration("""
                {"redirect_uris": ["http://localhost:3000/cb"]}
                """);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/.well-known/oauth-authorization-server",
                "/.well-known/oauth-authorization-server/mcp",
                "/.well-known/openid-configuration",
                "/.well-known/openid-configuration/mcp"
            })
    void shouldRewriteOnlyRegistrationEndpointInMetadata(String givenWellKnownEndpoint) throws JSONException {
        // Given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/.well-known/oauth-authorization-server"))
                        .willReturn(okJson(authorizationServerMetadata())));
        var headers = new HttpHeaders();
        headers.set("X-Forwarded-Host", "mcp.example.com");
        headers.set("X-Forwarded-Proto", "https");

        // When
        var response = restTemplate.exchange(
                "http://localhost:" + port + givenWellKnownEndpoint,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var upstream = staticWireMockServer.baseUrl();
        var expectedJson = """
                {
                  "issuer": "%1$s",
                  "authorization_endpoint": "%1$s/auth",
                  "token_endpoint": "%1$s/token",
                  "registration_endpoint": "https://mcp.example.com/mcp/oauth/register",
                  "mtls_endpoint_aliases": {
                    "token_endpoint": "%1$s/token",
                    "registration_endpoint": "https://mcp.example.com/mcp/oauth/register"
                  },
                  "scopes_supported": ["user:read", "user:write"]
                }
                """.formatted(upstream);
        JSONAssert.assertEquals(expectedJson, response.getBody(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void shouldAdvertiseMcpServerAsAuthorizationServer() throws JSONException {
        // When
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/.well-known/oauth-protected-resource",
                HttpMethod.GET,
                null,
                String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JSONAssert.assertEquals("""
                {
                  "resource": "http://localhost:%1$d/mcp",
                  "authorization_servers": ["http://localhost:%1$d/mcp"]
                }
                """.formatted(port), response.getBody(), JSONCompareMode.LENIENT);
    }

    private ResponseEntity<String> postRegistration(String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "http://localhost:" + port + "/mcp/oauth/register",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
    }

    private String authorizationServerMetadata() {
        var upstream = staticWireMockServer.baseUrl();
        return """
                {
                  "issuer": "%1$s",
                  "authorization_endpoint": "%1$s/auth",
                  "token_endpoint": "%1$s/token",
                  "registration_endpoint": "%1$s%2$s",
                  "mtls_endpoint_aliases": {
                    "token_endpoint": "%1$s/token",
                    "registration_endpoint": "%1$s%2$s"
                  }
                }
                """.formatted(upstream, UPSTREAM_REGISTRATION_PATH);
    }
}
