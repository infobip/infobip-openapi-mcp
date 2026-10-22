package com.infobip.openapi.mcp.auth.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.infobip.openapi.mcp.auth.OAuthProperties;
import java.net.URI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class UpstreamEndpointsLoaderTest {

    private static final String OPENID_CONFIGURATION = "/.well-known/openid-configuration";

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    void shouldReadAllEndpointsFromMetadata() {
        // Given
        var upstream = wireMockServer.baseUrl();
        givenMetadata("""
                {"registration_endpoint": "%1$s/register", "authorization_endpoint": "%1$s/auth",
                 "token_endpoint": "%1$s/token"}
                """.formatted(upstream));

        // When
        var endpoints = load();

        // Then
        then(endpoints)
                .usingRecursiveComparison()
                .isEqualTo(new UpstreamEndpoints(
                        URI.create(upstream + "/register"),
                        URI.create(upstream + "/auth"),
                        URI.create(upstream + "/token")));
        wireMockServer.verify(1, getRequestedFor(urlEqualTo(OPENID_CONFIGURATION)));
    }

    @Test
    void shouldFailWhenRegistrationEndpointIsMissing() {
        // Given
        givenMetadata("""
                {"authorization_endpoint": "%1$s/auth", "token_endpoint": "%1$s/token"}
                """.formatted(wireMockServer.baseUrl()));

        // When & Then
        thenThrownBy(() -> load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authorization server metadata has no registration_endpoint");
    }

    @Test
    void shouldFailWhenAuthorizationEndpointIsMissing() {
        // Given
        givenMetadata("""
                {"registration_endpoint": "%1$s/register", "token_endpoint": "%1$s/token"}
                """.formatted(wireMockServer.baseUrl()));

        // When & Then
        thenThrownBy(() -> load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authorization server metadata has no authorization_endpoint");
    }

    @Test
    void shouldFailWhenTokenEndpointIsMissing() {
        // Given
        givenMetadata("""
                {"registration_endpoint": "%1$s/register", "authorization_endpoint": "%1$s/auth"}
                """.formatted(wireMockServer.baseUrl()));

        // When & Then
        thenThrownBy(() -> load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authorization server metadata has no token_endpoint");
    }

    @Test
    void shouldFailWhenMetadataIsNotAvailable() {
        // Given
        wireMockServer.stubFor(
                get(urlEqualTo(OPENID_CONFIGURATION)).willReturn(aResponse().withStatus(500)));

        // When & Then
        thenThrownBy(() -> load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to read authorization server metadata at " + wireMockServer.baseUrl()
                        + OPENID_CONFIGURATION);
    }

    @Test
    void shouldFailWhenMetadataIsNotJson() {
        // Given
        givenMetadata("not json");

        // When & Then
        thenThrownBy(() -> load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to read authorization server metadata at " + wireMockServer.baseUrl()
                        + OPENID_CONFIGURATION);
    }

    private static void givenMetadata(String metadata) {
        wireMockServer.stubFor(get(urlEqualTo(OPENID_CONFIGURATION)).willReturn(okJson(metadata)));
    }

    private static UpstreamEndpoints load() {
        var oAuthProperties =
                new OAuthProperties(true, URI.create(wireMockServer.baseUrl()), null, null, null, null, true);
        return UpstreamEndpointsLoader.load(
                RestClient.create(), oAuthProperties, JsonMapper.builder().build());
    }
}
