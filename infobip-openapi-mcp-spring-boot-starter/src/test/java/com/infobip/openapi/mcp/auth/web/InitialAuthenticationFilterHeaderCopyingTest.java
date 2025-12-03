package com.infobip.openapi.mcp.auth.web;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test-http")
@TestPropertySource(properties = {"infobip.openapi.mcp.security.auth.override-external-response=false"})
class InitialAuthenticationFilterHeaderCopyingTest extends AuthenticationTestBase {

    @Test
    void shouldCopyAllHeadersExceptHopByHopWhenActingAsProxy() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .willReturn(aResponse()
                                .withStatus(401)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("X-Custom-Error-Code", "AUTH_FAILED")
                                .withHeader("X-Rate-Limit-Remaining", "42")
                                .withHeader("Cache-Control", "no-cache")
                                .withHeader("Vary", "Accept, Authorization")
                                .withHeader("X-Request-ID", "req-12345")
                                // Hop-by-hop headers that should NOT be copied
                                .withHeader("Connection", "close")
                                .withHeader("Transfer-Encoding", "chunked")
                                .withHeader("Upgrade", "h2c")
                                .withHeader("Proxy-Authorization", "Bearer proxy-token")
                                .withBody("{\"error\":\"Invalid credentials\",\"code\":\"AUTH_001\"}")));

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
        then(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Should copy the downstream response body as-is
        then(response.getBody()).isEqualTo("{\"error\":\"Invalid credentials\",\"code\":\"AUTH_001\"}");

        // Should copy all non-hop-by-hop headers from downstream
        then(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        then(response.getHeaders().getFirst("X-Custom-Error-Code")).isEqualTo("AUTH_FAILED");
        then(response.getHeaders().getFirst("X-Rate-Limit-Remaining")).isEqualTo("42");
        then(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-cache");
        then(response.getHeaders().getFirst("Vary")).isEqualTo("Accept, Authorization");
        then(response.getHeaders().getFirst("X-Request-ID")).isEqualTo("req-12345");

        // Should NOT copy hop-by-hop headers
        then(response.getHeaders().getFirst("Connection")).isNull();
        then(response.getHeaders().getFirst("Transfer-Encoding")).isNull();
        then(response.getHeaders().getFirst("Upgrade")).isNull();
        then(response.getHeaders().getFirst("Proxy-Authorization")).isNull();
    }

    @Test
    void shouldCopyHeadersWithMultipleValues() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .willReturn(aResponse()
                                .withStatus(403)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("Set-Cookie", "session=abc123; Path=/")
                                .withHeader("Set-Cookie", "csrf=xyz789; HttpOnly")
                                .withHeader("X-Custom-Header", "value1")
                                .withHeader("X-Custom-Header", "value2")
                                // Hop-by-hop header that should not be copied
                                .withHeader("Keep-Alive", "timeout=5, max=1000")
                                .withBody("{\"error\":\"Insufficient permissions\"}")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer limited-token");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
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
        then(response.getBody()).isEqualTo("{\"error\":\"Insufficient permissions\"}");

        // Should copy headers with multiple values
        then(response.getHeaders().get("Set-Cookie"))
                .containsExactlyInAnyOrder("session=abc123; Path=/", "csrf=xyz789; HttpOnly");
        then(response.getHeaders().get("X-Custom-Header")).containsExactlyInAnyOrder("value1", "value2");

        // Should NOT copy hop-by-hop headers
        then(response.getHeaders().getFirst("Keep-Alive")).isNull();
    }

    @Test
    void shouldHandleCaseInsensitiveHopByHopHeaders() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .willReturn(aResponse()
                                .withStatus(401)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("X-Safe-Header", "should-be-copied")
                                // Test case variations of hop-by-hop headers
                                .withHeader("CONNECTION", "close")
                                .withHeader("Transfer-encoding", "chunked")
                                .withHeader("UPGRADE", "websocket")
                                .withHeader("te", "gzip")
                                .withBody("{\"error\":\"Authentication failed\"}")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer test-token");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
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
        then(response.getBody()).isEqualTo("{\"error\":\"Authentication failed\"}");

        // Should copy safe headers
        then(response.getHeaders().getFirst("X-Safe-Header")).isEqualTo("should-be-copied");

        // Should NOT copy hop-by-hop headers regardless of case
        then(response.getHeaders().getFirst("CONNECTION")).isNull();
        then(response.getHeaders().getFirst("Transfer-encoding")).isNull();
        then(response.getHeaders().getFirst("UPGRADE")).isNull();
        then(response.getHeaders().getFirst("te")).isNull();
    }

    @Test
    void shouldCopyResponseWithDifferentContentTypes() {
        // given
        getStaticWireMockServer()
                .stubFor(get(urlEqualTo("/auth/validate"))
                        .willReturn(aResponse()
                                .withStatus(400)
                                .withHeader("Content-Type", "application/xml; charset=utf-8")
                                .withHeader("X-API-Version", "v2.1")
                                .withHeader("X-Error-Source", "auth-service")
                                // Should not copy this hop-by-hop header
                                .withHeader("Trailers", "X-Custom-Trailer")
                                .withBody("<error><message>Bad request format</message></error>")));

        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer malformed");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
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
        then(response.getBody()).isEqualTo("<error><message>Bad request format</message></error>");

        // Should preserve original content type from downstream
        then(response.getHeaders().getFirst("Content-Type")).isEqualTo("application/xml;charset=utf-8");
        then(response.getHeaders().getFirst("X-API-Version")).isEqualTo("v2.1");
        then(response.getHeaders().getFirst("X-Error-Source")).isEqualTo("auth-service");

        // Should NOT copy hop-by-hop header
        then(response.getHeaders().getFirst("Trailers")).isNull();
    }
}
