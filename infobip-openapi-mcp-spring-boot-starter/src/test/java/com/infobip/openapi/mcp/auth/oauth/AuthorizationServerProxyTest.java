package com.infobip.openapi.mcp.auth.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import com.github.tomakehurst.wiremock.http.Fault;
import com.infobip.openapi.mcp.auth.scope.ScopeDiscoveryService;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

@ActiveProfiles("test-http")
@DirtiesContext
@TestPropertySource(
        properties = {
            "infobip.openapi.mcp.security.auth.oauth.scope-discovery.scope-extensions=x-scopes",
            "infobip.openapi.mcp.security.auth.oauth.scope-discovery.mandatory-scopes=profile,offline_access",
            "infobip.openapi.mcp.security.auth.oauth.registration-proxy.enabled=true"
        })
public class AuthorizationServerProxyTest extends OAuthTestBase {

    private static final String OPENID_CONFIGURATION = "/.well-known/openid-configuration";
    private static final String UPSTREAM_REGISTRATION_PATH = "/clients-registrations/openid-connect";
    private static final String UPSTREAM_TOKEN_PATH = "/token";
    private static final String CLIENT_REDIRECT_URI = "http://localhost:3000/cb";
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
                {"client_id": "abc", "scope": "user:read user:write", "redirect_uris": ["%s"]}
                """.formatted(callbackUri(CLIENT_REDIRECT_URI));
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
        JSONAssert.assertEquals("""
                {"client_id": "abc", "scope": "user:read user:write", "redirect_uris": ["%s"]}
                """.formatted(CLIENT_REDIRECT_URI), response.getBody(), JSONCompareMode.STRICT);

        var forwardedRequests = getStaticWireMockServer()
                .findAll(postRequestedFor(urlEqualTo(UPSTREAM_REGISTRATION_PATH))
                        .withHeader("Authorization", equalTo("Bearer initial-access-token"))
                        .withHeader("X-Forwarded-For", equalTo("203.0.113.7, 127.0.0.1")));
        then(forwardedRequests).hasSize(1);
        var forwardedScope = new JSONObject(forwardedRequests.getFirst().getBodyAsString()).getString("scope");
        then(forwardedScope.split(" "))
                .containsExactlyInAnyOrder("user:read", "user:write", "profile", "offline_access");
        JSONAssert.assertEquals(
                """
                {"client_name": "Test client", "redirect_uris": ["%s"]}
                """.formatted(callbackUri(CLIENT_REDIRECT_URI)),
                forwardedRequests.getFirst().getBodyAsString(),
                JSONCompareMode.LENIENT);
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
                .containsExactlyInAnyOrder("user:read", "user:write", "profile", "offline_access");
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
    void shouldRewriteIssuerAndProxiedEndpointsInMetadata(String givenWellKnownEndpoint) throws JSONException {
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
                  "issuer": "https://mcp.example.com/mcp",
                  "authorization_endpoint": "https://mcp.example.com/mcp/oauth/authorize",
                  "token_endpoint": "https://mcp.example.com/mcp/oauth/token",
                  "registration_endpoint": "https://mcp.example.com/mcp/oauth/register",
                  "userinfo_endpoint": "%1$s/userinfo",
                  "authorization_response_iss_parameter_supported": true,
                  "mtls_endpoint_aliases": {
                    "token_endpoint": "https://mcp.example.com/mcp/oauth/token",
                    "registration_endpoint": "https://mcp.example.com/mcp/oauth/register",
                    "userinfo_endpoint": "%1$s/userinfo"
                  },
                  "scopes_supported": ["user:read", "user:write", "profile", "offline_access"]
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

    @Test
    void shouldRedirectAuthorizationRequestWithCallbackUri() {
        // When
        var response = restTemplate
                .withRedirects(HttpRedirects.DONT_FOLLOW)
                .getForEntity(
                        URI.create("http://localhost:" + port + "/mcp/oauth/authorize?response_type=code&client_id=abc"
                                + "&redirect_uri=" + urlEncode(CLIENT_REDIRECT_URI)
                                + "&state=a%2Bb%26c&code_challenge=xyz&code_challenge_method=S256"
                                + "&scope=user%3Aread+openid"),
                        String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        var location = UriComponentsBuilder.fromUri(response.getHeaders().getLocation())
                .build();
        then(location.getScheme() + "://" + location.getHost() + ":" + location.getPort() + location.getPath())
                .isEqualTo(staticWireMockServer.baseUrl() + "/auth");
        var parameters = decodedQueryParameters(response.getHeaders().getLocation());
        then(parameters)
                .containsEntry("response_type", "code")
                .containsEntry("client_id", "abc")
                .containsEntry("redirect_uri", callbackUri(CLIENT_REDIRECT_URI))
                .containsEntry("state", "a+b&c")
                .containsEntry("code_challenge", "xyz")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("scope", "user:read openid");
    }

    @Test
    void shouldRedirectCallbackToClientWithMcpServerIssuer() {
        // When
        var response = restTemplate
                .withRedirects(HttpRedirects.DONT_FOLLOW)
                .getForEntity(
                        URI.create(callbackUri(CLIENT_REDIRECT_URI) + "?code=the-code&state=a%2Bb%26c"
                                + "&session_state=s1&iss=" + urlEncode(staticWireMockServer.baseUrl())),
                        String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        var location = response.getHeaders().getLocation();
        then(location.toString()).startsWith(CLIENT_REDIRECT_URI + "?");
        then(decodedQueryParameters(location))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "code", "the-code",
                        "state", "a+b&c",
                        "session_state", "s1",
                        "iss", "http://localhost:" + port + "/mcp"));
    }

    @Test
    void shouldRedirectCallbackErrorToClient() {
        // When
        var response = restTemplate
                .withRedirects(HttpRedirects.DONT_FOLLOW)
                .getForEntity(
                        URI.create(callbackUri("http://127.0.0.1:1455/callback?keep=1")
                                + "?error=access_denied&error_description=User+denied&state=s"),
                        String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        var location = response.getHeaders().getLocation();
        then(location.toString()).startsWith("http://127.0.0.1:1455/callback?keep=1&");
        then(decodedQueryParameters(location))
                .containsEntry("keep", "1")
                .containsEntry("error", "access_denied")
                .containsEntry("error_description", "User denied")
                .containsEntry("state", "s")
                .doesNotContainKey("iss");
    }

    @Test
    void shouldRejectCallbackWithInvalidEncodedRedirectUri() {
        // When
        var response = restTemplate
                .withRedirects(HttpRedirects.DONT_FOLLOW)
                .getForEntity("http://localhost:" + port + "/mcp/oauth/callback/not*base64?code=c", String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldForwardAuthorizationCodeTokenRequestWithCallbackUriAndRemoveIdToken() throws JSONException {
        // Given
        getStaticWireMockServer()
                .stubFor(post(urlEqualTo(UPSTREAM_TOKEN_PATH))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("Cache-Control", "no-store")
                                .withBody("""
                                        {"access_token": "at", "refresh_token": "rt", "id_token": "it",
                                         "token_type": "Bearer", "expires_in": 300}
                                        """)));
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth("abc", "secret");
        var givenForm = "grant_type=authorization_code&code=the-code&client_id=abc&code_verifier=v%2B1"
                + "&redirect_uri=" + urlEncode(CLIENT_REDIRECT_URI);

        // When
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/oauth/token",
                HttpMethod.POST,
                new HttpEntity<>(givenForm, headers),
                String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        JSONAssert.assertEquals("""
                {"access_token": "at", "refresh_token": "rt", "token_type": "Bearer", "expires_in": 300}
                """, response.getBody(), JSONCompareMode.STRICT);
        var forwardedRequests = getStaticWireMockServer()
                .findAll(postRequestedFor(urlEqualTo(UPSTREAM_TOKEN_PATH))
                        .withHeader("Authorization", equalTo(headers.getFirst("Authorization")))
                        .withHeader("Content-Type", containing("application/x-www-form-urlencoded")));
        then(forwardedRequests).hasSize(1);
        then(decodedForm(forwardedRequests.getFirst().getBodyAsString()))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "grant_type", "authorization_code",
                        "code", "the-code",
                        "client_id", "abc",
                        "code_verifier", "v+1",
                        "redirect_uri", callbackUri(CLIENT_REDIRECT_URI)));
    }

    @Test
    void shouldForwardRefreshTokenRequestUnchanged() {
        // Given
        getStaticWireMockServer()
                .stubFor(post(urlEqualTo(UPSTREAM_TOKEN_PATH))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"access_token\": \"at2\"}")));

        // When
        var response = postForm("/mcp/oauth/token", "grant_type=refresh_token&refresh_token=rt&client_id=abc");

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var forwardedBody = getStaticWireMockServer()
                .findAll(postRequestedFor(urlEqualTo(UPSTREAM_TOKEN_PATH)))
                .getFirst()
                .getBodyAsString();
        then(decodedForm(forwardedBody))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("grant_type", "refresh_token", "refresh_token", "rt", "client_id", "abc"));
    }

    @Test
    void shouldPassThroughTokenErrors() throws JSONException {
        // Given
        var givenError = """
                {"error": "invalid_grant", "error_description": "Code not valid"}
                """;
        getStaticWireMockServer()
                .stubFor(post(urlEqualTo(UPSTREAM_TOKEN_PATH))
                        .willReturn(aResponse()
                                .withStatus(400)
                                .withHeader("Content-Type", "application/json")
                                .withBody(givenError)));

        // When
        var response = postForm("/mcp/oauth/token", "grant_type=authorization_code&code=bad");

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JSONAssert.assertEquals(givenError, response.getBody(), JSONCompareMode.STRICT);
    }

    private ResponseEntity<String> postForm(String path, String form) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.exchange(
                "http://localhost:" + port + path, HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    }

    private String callbackUri(String redirectUri) {
        return "http://localhost:" + port + "/mcp/oauth/callback/"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(redirectUri.getBytes(StandardCharsets.UTF_8));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Map<String, String> decodedQueryParameters(URI uri) {
        return decodedForm(uri.getRawQuery());
    }

    private static Map<String, String> decodedForm(String form) {
        var parameters = new LinkedHashMap<String, String>();
        for (var pair : form.split("&")) {
            var separator = pair.indexOf('=');
            parameters.put(
                    URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return parameters;
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
                  "userinfo_endpoint": "%1$s/userinfo",
                  "pushed_authorization_request_endpoint": "%1$s/par",
                  "require_pushed_authorization_requests": false,
                  "authorization_response_iss_parameter_supported": true,
                  "mtls_endpoint_aliases": {
                    "token_endpoint": "%1$s/token",
                    "registration_endpoint": "%1$s%2$s",
                    "userinfo_endpoint": "%1$s/userinfo",
                    "pushed_authorization_request_endpoint": "%1$s/par"
                  }
                }
                """.formatted(upstream, UPSTREAM_REGISTRATION_PATH);
    }
}
