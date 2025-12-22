package com.infobip.openapi.mcp.enricher;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class XForwardedHostEnricherTest {

    @InjectMocks
    private XForwardedHostEnricher givenXForwardedHostEnricher;

    @Mock
    private RestClient.RequestHeadersSpec<?> spec;

    @Spy
    private XForwardedHostCalculator xForwardedHostCalculator =
            new XForwardedHostCalculator(Optional.empty(), Optional.empty());

    private static final String X_FORWARDED_HOST_HEADER = "X-Forwarded-Host";
    private static final String X_FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";
    private static final String X_FORWARDED_PORT_HEADER = "X-Forwarded-Port";
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PROTOCOL = "http";
    private final MockHttpServletRequest givenMockRequest = new MockHttpServletRequest();

    private McpRequestContext createTestContext(MockHttpServletRequest request) {
        return new McpRequestContext(request);
    }

    private McpRequestContext createTestContextWithoutRequest() {
        return new McpRequestContext();
    }

    @BeforeEach
    void setUp() {
        givenMockRequest.clearAttributes();
        lenient().doReturn(spec).when(spec).header(anyString(), anyString());
    }

    @Test
    void shouldForwardXForwardedHostHeaderWhenPresent() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "api.example.com");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, "api.example.com");
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, DEFAULT_PROTOCOL);
        then(spec).should(never()).header(eq(X_FORWARDED_PORT_HEADER), anyString());
    }

    @Test
    void shouldForwardComplexHostValue() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "subdomain.api.example.com");
        givenMockRequest.addHeader(X_FORWARDED_PROTO_HEADER, "https");
        givenMockRequest.addHeader(X_FORWARDED_PORT_HEADER, "8080");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, "subdomain.api.example.com");
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, "https");
        then(spec).should().header(X_FORWARDED_PORT_HEADER, "8080");
    }

    @Test
    void shouldAddHostHeaderWhenXForwardedHostIsMissing() {
        // given
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, DEFAULT_HOST);
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, DEFAULT_PROTOCOL);
        then(spec).shouldHaveNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldNotAddHeaderWhenXForwardedHostIsEmpty(String emptyValue) {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, DEFAULT_HOST);
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, DEFAULT_PROTOCOL);
        then(spec).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldNotAddHeaderWhenRequestIsNull() {
        // given
        var context = createTestContextWithoutRequest();

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).shouldHaveNoInteractions();
    }

    @Test
    void shouldForwardIPv4HostAddress() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "192.168.1.100");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, "192.168.1.100");
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, DEFAULT_PROTOCOL);
        then(spec).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldForwardIPv6HostAddress() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "[2001:db8::1]");
        givenMockRequest.addHeader(X_FORWARDED_PORT_HEADER, "1234");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, "[2001:db8::1]");
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, DEFAULT_PROTOCOL);
        then(spec).should().header(X_FORWARDED_PORT_HEADER, "1234");
    }

    @Test
    void shouldUseHostHeaderWhenXForwardedHostMissing() {
        // given
        givenMockRequest.addHeader("Host", "api.example.com");
        givenMockRequest.addHeader(X_FORWARDED_PROTO_HEADER, "https");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, "api.example.com");
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, "https");
        then(spec).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldPreferXForwardedHostOverHost() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "original.example.com");
        givenMockRequest.addHeader(X_FORWARDED_PROTO_HEADER, "https");
        givenMockRequest.addHeader("Host", "proxy.example.com");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, "original.example.com");
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, "https");
        then(spec).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldUseHostWithPort() {
        // given
        givenMockRequest.addHeader("Host", "api.example.com:8080");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, "api.example.com");
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, DEFAULT_PROTOCOL);
        then(spec).should().header(X_FORWARDED_PORT_HEADER, "8080");
    }

    @Test
    void shouldUseHostWithIPv4() {
        // given
        givenMockRequest.addHeader("Host", "192.168.1.100");
        // No X-Forwarded-Host header
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, "192.168.1.100");
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, DEFAULT_PROTOCOL);
        then(spec).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldUseHostWithIPv6() {
        // given
        givenMockRequest.addHeader("Host", "[2001:db8::1]");
        givenMockRequest.addHeader(X_FORWARDED_PORT_HEADER, "4321");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, "[2001:db8::1]");
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, DEFAULT_PROTOCOL);
        then(spec).should().header(X_FORWARDED_PORT_HEADER, "4321");
    }

    @Test
    void shouldNotAddHeaderWhenBothBlank() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "   ");
        givenMockRequest.addHeader("Host", "   ");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).shouldHaveNoInteractions();
    }

    @Test
    void shouldSetXForwardedPortForNonDefaultHttpsPort() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "api.example.com");
        givenMockRequest.addHeader(X_FORWARDED_PORT_HEADER, "8443");
        givenMockRequest.addHeader(X_FORWARDED_PROTO_HEADER, "https");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, "api.example.com");
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, "https");
        then(spec).should().header(X_FORWARDED_PORT_HEADER, "8443");
    }

    @Test
    void shouldNotSetXForwardedPortForDefaultHttpPort() {
        // given
        givenMockRequest.addHeader("Host", "api.example.com:80");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, "api.example.com");
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, "http");
        then(spec).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldNotSetXForwardedPortForDefaultHttpsPort() {
        // given
        givenMockRequest.addHeader("Host", "api.example.com:443");
        var context = createTestContext(givenMockRequest);

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).should().header(X_FORWARDED_HOST_HEADER, "api.example.com");
        then(spec).should().header(X_FORWARDED_PROTO_HEADER, DEFAULT_PROTOCOL);
        then(spec).should().header(X_FORWARDED_PORT_HEADER, "443");
    }
}
