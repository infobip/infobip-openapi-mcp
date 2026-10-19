package com.infobip.openapi.mcp.auth.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test-http")
@DirtiesContext
public class AuthorizationServerProxyUpstreamUrlTest extends OAuthTestBase {

    private static final String CONFIGURED_REGISTRATION_PATH = "/custom/register";

    @DynamicPropertySource
    static void configureRegistrationProxy(DynamicPropertyRegistry registry) {
        registry.add("infobip.openapi.mcp.security.auth.oauth.registration-proxy.enabled", () -> "true");
        registry.add("infobip.openapi.mcp.security.auth.oauth.registration-proxy.path", () -> "/register");
        registry.add(
                "infobip.openapi.mcp.security.auth.oauth.registration-proxy.upstream-url",
                () -> staticWireMockServer.baseUrl() + CONFIGURED_REGISTRATION_PATH);
    }

    @Test
    void shouldUseConfiguredPathAndUpstreamUrlWithoutReadingMetadata() {
        // Given
        getStaticWireMockServer().resetAll();
        getStaticWireMockServer()
                .stubFor(post(urlEqualTo(CONFIGURED_REGISTRATION_PATH))
                        .willReturn(aResponse().withStatus(201).withBody("{}")));
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // When
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/register",
                HttpMethod.POST,
                new HttpEntity<>("{\"redirect_uris\": [\"http://localhost:3000/cb\"]}", headers),
                String.class);

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        getStaticWireMockServer().verify(1, postRequestedFor(urlEqualTo(CONFIGURED_REGISTRATION_PATH)));
        getStaticWireMockServer().verify(0, getRequestedFor(urlEqualTo("/.well-known/openid-configuration")));
    }
}
