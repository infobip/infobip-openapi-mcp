package com.infobip.openapi.mcp.auth.web;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test-http")
class AuthenticationTimeoutAndUserAgentTest extends AuthenticationTestBase {

    @Test
    void shouldTimeoutWhenAuthServiceIsSlow() {
        // given - This tests READ TIMEOUT
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withFixedDelay(2500))); // 2.5 seconds delay, readTimeout is 2 seconds

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer test-token");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>("""
                {
                    "jsonrpc": "2.0",
                    "method": "tools/list",
                    "id": 1
                }
                """, headers);

        // when
        var response =
                restTemplate.exchange("http://localhost:" + port + "/mcp", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");
    }

    @Test
    void shouldSendCorrectUserAgent() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .withHeader("User-Agent", equalTo("openapi-mcp-test"))
                        .willReturn(aResponse().withStatus(200)));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer test-token");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>("""
                {
                    "jsonrpc": "2.0",
                    "method": "tools/list",
                    "id": 1
                }
                """, headers);

        // when
        var response =
                restTemplate.exchange("http://localhost:" + port + "/mcp", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        getStaticWireMockServer()
                .verify(getRequestedFor(urlEqualTo("/auth/validate"))
                        .withHeader("User-Agent", equalTo("openapi-mcp-test")));
    }
}
