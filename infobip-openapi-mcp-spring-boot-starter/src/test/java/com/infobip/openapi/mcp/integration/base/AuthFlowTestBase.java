package com.infobip.openapi.mcp.integration.base;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;

import org.junit.jupiter.api.Test;

public abstract class AuthFlowTestBase extends IntegrationTestBase {

    @Test
    void shouldPassWithCorrectAuthHeader() {
        // Given
        var givenAuthHeaderValue = "Bearer correct-token";

        givenOpenAPISpecification("/openapi/base.json");
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .withHeader("Authorization", equalTo(givenAuthHeaderValue))
                        .willReturn(aResponse().withStatus(200).withBody("Authentication successful")));

        try (var givenMcpClient = givenMcpClientWithAuthHeader(givenAuthHeaderValue)) {
            // When
            givenMcpClient.initialize();
            var tools = givenMcpClient.listTools().tools();

            // Then
            then(tools).isNotEmpty();
        }
    }

    @Test
    void shouldRejectRequestWithoutAuthHeader() {
        // Given
        var givenAuthHeaderValue = "";

        givenOpenAPISpecification("/openapi/base.json");
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .withHeader("Authorization", absent())
                        .willReturn(aResponse()
                                .withStatus(400)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"error\":\"Access denied\"}")));

        try (var givenMcpClient = givenMcpClientWithAuthHeader(givenAuthHeaderValue)) {
            // When
            var thrown = catchThrowable(givenMcpClient::initialize);

            // Then
            then(thrown).isInstanceOf(RuntimeException.class);
            then(thrown.getMessage()).isEqualTo("Client failed to initialize by explicit API call");
        }
    }

    @Test
    void shouldRejectRequestWithWrongAuthHeader() {
        // Given
        var givenAuthHeaderValue = "Bearer wrong-token";

        givenOpenAPISpecification("/openapi/base.json");
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .withHeader("Authorization", equalTo(givenAuthHeaderValue))
                        .willReturn(aResponse()
                                .withStatus(403)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"error\":\"Access denied\"}")));

        try (var givenMcpClient = givenMcpClientWithAuthHeader(givenAuthHeaderValue)) {
            // When
            var thrown = catchThrowable(givenMcpClient::initialize);

            // Then
            then(thrown).isInstanceOf(RuntimeException.class);
            then(thrown.getMessage()).isEqualTo("Client failed to initialize by explicit API call");
        }
    }

    @Test
    void shouldTimeoutWithAnyAuth() {
        // Given
        var givenAuthHeaderValue = "Bearer correct-token";

        givenOpenAPISpecification("/openapi/base.json");
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .withHeader("Authorization", equalTo(givenAuthHeaderValue))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withBody("Authentication successful")
                                .withFixedDelay(1500)));

        try (var givenMcpClient = givenMcpClientWithAuthHeader(givenAuthHeaderValue)) {
            // When
            var thrown = catchThrowable(givenMcpClient::initialize);

            // Then
            then(thrown).isInstanceOf(RuntimeException.class);
            then(thrown.getMessage()).isEqualTo("Client failed to initialize by explicit API call");
        }
    }
}
