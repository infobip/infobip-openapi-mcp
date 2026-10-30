package com.infobip.openapi.mcp.auth.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.auth.web.CallbackPolicy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test-http")
@DirtiesContext
@TestPropertySource(
        properties = {
            "infobip.openapi.mcp.security.auth.oauth.proxy-enabled=true",
            "logging.level.com.infobip.openapi.mcp.autoconfiguration.OAuthExchangeLogger=TRACE"
        })
class OAuthExchangeLoggerTest extends OAuthTestBase {

    private static final String UPSTREAM_REGISTRATION_PATH = "/clients-registrations/openid-connect";

    @TestConfiguration
    static class TestConfig {

        @Bean
        CallbackPolicy testCallbackPolicy() {
            return new HostnameCallbackPolicy("localhost", "127.0.0.1");
        }
    }

    @DynamicPropertySource
    static void stubAuthorizationServerMetadata(DynamicPropertyRegistry registry) {
        startWireMockServer();
        staticWireMockServer.stubFor(
                get(urlEqualTo("/.well-known/openid-configuration")).willReturn(okJson(authorizationServerMetadata())));
    }

    @BeforeEach
    void setUp() {
        getStaticWireMockServer().resetAll();
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
                        .willReturn(okJson(authorizationServerMetadata())));
    }

    @Test
    void shouldLogTokenRequestAndResponseWithoutLeakingCredentials(CapturedOutput output) {
        // Given
        getStaticWireMockServer()
                .stubFor(post(urlEqualTo("/token"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {"access_token": "secret-access", "refresh_token": "secret-refresh",
                                         "token_type": "Bearer", "expires_in": 300}
                                        """)));
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth("abc", "secret-client");

        // When
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/oauth/token",
                HttpMethod.POST,
                new HttpEntity<>("grant_type=refresh_token&refresh_token=secret-refresh&client_id=abc", headers),
                String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(output.getAll())
                .contains("OAuth request: POST /mcp/oauth/token")
                .contains("grant_type=[refresh_token]")
                .contains("client_id=[abc]")
                .contains("OAuth response: 200 for POST /mcp/oauth/token")
                .contains("\"token_type\":\"Bearer\"")
                .doesNotContain("secret-access")
                .doesNotContain("secret-refresh")
                .doesNotContain("secret-client");
    }

    @Test
    void shouldLogRegistrationBodyAndStillForwardIt(CapturedOutput output) {
        // Given
        getStaticWireMockServer()
                .stubFor(post(urlEqualTo(UPSTREAM_REGISTRATION_PATH))
                        .willReturn(aResponse()
                                .withStatus(201)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {"client_id": "abc", "registration_access_token": "secret-registration"}
                                        """)));
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // When
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/oauth/register",
                HttpMethod.POST,
                new HttpEntity<>("""
                        {"client_name": "Logged client", "redirect_uris": ["http://localhost:3000/cb"]}
                        """, headers),
                String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        then(getStaticWireMockServer()
                        .findAll(postRequestedFor(urlEqualTo(UPSTREAM_REGISTRATION_PATH)))
                        .getFirst()
                        .getBodyAsString())
                .contains("Logged client");
        then(output.getAll())
                .contains("OAuth request: POST /mcp/oauth/register")
                .contains("\"client_name\":\"Logged client\"")
                .contains("OAuth response: 201 for POST /mcp/oauth/register")
                .contains("\"client_id\":\"abc\"")
                .doesNotContain("secret-registration");
    }

    @Test
    void shouldMaskAuthorizationCodeInRedirectLocation(CapturedOutput output) {
        // Given
        var givenRedirectUri = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("http://localhost:3000/cb".getBytes(StandardCharsets.UTF_8));

        // When
        var response = restTemplate
                .withRedirects(HttpRedirects.DONT_FOLLOW)
                .getForEntity(
                        "http://localhost:" + port + "/mcp/oauth/callback/" + givenRedirectUri
                                + "?code=secret-code&state=the-state",
                        String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        then(response.getHeaders().getLocation().getRawQuery()).contains("code=secret-code");
        then(output.getAll())
                .contains("OAuth response: 302 for GET /mcp/oauth/callback/")
                .contains("code=***")
                .contains("state=the-state")
                .doesNotContain("secret-code");
    }

    @Test
    void shouldLogWellKnownEndpointResponse(CapturedOutput output) {
        // When
        var response = restTemplate.getForEntity(
                "http://localhost:" + port + "/.well-known/oauth-protected-resource", String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(output.getAll())
                .contains("OAuth request: GET /.well-known/oauth-protected-resource")
                .contains("OAuth response: 200 for GET /.well-known/oauth-protected-resource")
                .contains("\"authorization_servers\"");
    }

    private static String authorizationServerMetadata() {
        var upstream = staticWireMockServer.baseUrl();
        return """
                {
                  "issuer": "%1$s",
                  "authorization_endpoint": "%1$s/auth",
                  "token_endpoint": "%1$s/token",
                  "registration_endpoint": "%1$s%2$s"
                }
                """.formatted(upstream, UPSTREAM_REGISTRATION_PATH);
    }
}
