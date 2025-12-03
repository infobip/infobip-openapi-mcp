package com.infobip.openapi.mcp.auth.web;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test-sse")
class SseTransportAuthenticationFilterTest extends AuthenticationTestBase {

    @Test
    void shouldForwardXFFHeader() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .withHeader("Authorization", equalTo("Bearer valid-token"))
                        .withHeader("X-Forwarded-For", matching(".*"))
                        .willReturn(aResponse().withStatus(200).withBody("Authentication successful")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer valid-token");
        headers.set("X-Forwarded-For", "192.0.2.1, 198.51.100.10, 127.0.0.1");
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
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/message", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        getStaticWireMockServer()
                .verify(getRequestedFor(urlEqualTo("/auth/validate"))
                        .withHeader("Authorization", equalTo("Bearer valid-token"))
                        .withHeader("X-Forwarded-For", equalTo("192.0.2.1, 198.51.100.10, 127.0.0.1")));
    }

    @Test
    void shouldAddXFFHeaderWhenEmpty() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .withHeader("Authorization", equalTo("Bearer valid-token"))
                        .withHeader("X-Forwarded-For", matching(".*"))
                        .willReturn(aResponse().withStatus(200).withBody("Authentication successful")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer valid-token");
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
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/message", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        getStaticWireMockServer()
                .verify(getRequestedFor(urlEqualTo("/auth/validate"))
                        .withHeader("Authorization", equalTo("Bearer valid-token"))
                        .withHeader("X-Forwarded-For", equalTo("127.0.0.1")));
    }

    @Test
    void shouldAddXFFHeader() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .withHeader("Authorization", equalTo("Bearer valid-token"))
                        .withHeader("X-Forwarded-For", matching(".*"))
                        .willReturn(aResponse().withStatus(200).withBody("Authentication successful")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer valid-token");
        headers.set("X-Forwarded-For", "198.51.100.10");
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
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/message", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        getStaticWireMockServer()
                .verify(getRequestedFor(urlEqualTo("/auth/validate"))
                        .withHeader("Authorization", equalTo("Bearer valid-token"))
                        .withHeader("X-Forwarded-For", equalTo("198.51.100.10, 127.0.0.1")));
    }

    @Test
    void shouldAllowAccessWithValidAuthentication() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .withHeader("Authorization", equalTo("Bearer valid-token"))
                        .willReturn(aResponse().withStatus(200).withBody("Authentication successful")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer valid-token");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>("""
                {
                    "jsonrpc": "2.0",
                    "method": "tools/list",
                    "id": 1
                }
                """, headers);

        // when - using SSE message endpoint
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/message", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        then(response.getBody().toString()).contains("Session ID missing in message endpoint");
        getStaticWireMockServer()
                .verify(getRequestedFor(urlEqualTo("/auth/validate"))
                        .withHeader("Authorization", equalTo("Bearer valid-token")));
    }

    @Test
    void shouldReturnExternalResponseWhenOverrideDisabled() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .willReturn(aResponse()
                                .withStatus(403)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"error\":\"Access denied\"}")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer invalid-token");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>("""
                {
                    "jsonrpc": "2.0",
                    "method": "tools/list",
                    "id": 1
                }
                """, headers);

        // when - using SSE message endpoint
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/message", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        then(response.getHeaders().getContentType().toString()).contains("application/json");
        then(response.getBody()).isEqualTo("{\"error\":\"Access denied\"}");
    }

    @Test
    void shouldHandleAuthServiceNetworkFailure() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .willReturn(aResponse()
                                .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer any-token");
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
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/message", HttpMethod.POST, entity, String.class);

        // then
        then(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        then(response.getHeaders().getContentType().toString()).isEqualTo("application/json;charset=UTF-8");
    }

    @Test
    void shouldHandleMultipleScenarios() {
        // Test 401 Unauthorized
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .inScenario("auth-flow")
                        .whenScenarioStateIs("Started")
                        .willReturn(aResponse().withStatus(401))
                        .willSetStateTo("unauthorized"));

        // Test 403 Forbidden
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .inScenario("auth-flow")
                        .whenScenarioStateIs("unauthorized")
                        .willReturn(aResponse().withStatus(403))
                        .willSetStateTo("forbidden"));

        // Test 200 OK
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .inScenario("auth-flow")
                        .whenScenarioStateIs("forbidden")
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

        // First call - should get 401
        var response1 = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/message", HttpMethod.POST, entity, String.class);
        then(response1.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        then(response1.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNullOrEmpty();

        // Second call - should get 403
        var response2 = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/message", HttpMethod.POST, entity, String.class);
        then(response2.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Third call - should get 200
        var response3 = restTemplate.exchange(
                "http://localhost:" + port + "/mcp/message", HttpMethod.POST, entity, String.class);
        // Session ID is missing in message endpoint, so it should return BAD_REQUEST
        then(response3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldVerifyFilterIsRegistered() {
        // then
        then(applicationContext.getBeansOfType(FilterRegistrationBean.class).values().stream()
                        .map(FilterRegistrationBean::getFilter)
                        .map(bean -> (Class) bean.getClass())
                        .toList())
                .contains(InitialAuthenticationFilter.class);
    }
}
