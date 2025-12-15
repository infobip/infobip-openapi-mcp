package com.infobip.openapi.mcp.enricher;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

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
    private static final String DEFAULT_PROTOCOL = "http";
    private static final String DEFAULT_PROTOCOL_PORT = "-1";
    private final MockHttpServletRequest givenMockRequest = new MockHttpServletRequest();

    private McpRequestContext createTestContext(MockHttpServletRequest request) {
        return new McpRequestContext(request, null, null, null);
    }

    private McpRequestContext createTestContextWithoutRequest() {
        return new McpRequestContext(null, null, null, null);
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
        then(spec).should().header(eq(X_FORWARDED_HOST_HEADER), eq("api.example.com"));
        then(spec).should().header(eq(X_FORWARDED_PROTO_HEADER), eq(DEFAULT_PROTOCOL));
        then(spec).should(never()).header(eq(X_FORWARDED_PORT_HEADER), eq(DEFAULT_PROTOCOL_PORT));
    }

    @Test
    void shouldForwardComplexHostValue() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "subdomain.api.example.com:8080");
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
    void shouldNotAddHeaderWhenXForwardedHostIsMissing() {
        // given
        var context = createTestContext(new MockHttpServletRequest());

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        then(spec).shouldHaveNoInteractions();
    }

    @Test
    void shouldNotAddHeaderWhenXForwardedHostIsEmpty() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "");
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest)).thenReturn(UriComponentsBuilder.newInstance());

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldNotAddHeaderWhenXForwardedHostIsBlank() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "   ");
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest)).thenReturn(UriComponentsBuilder.newInstance());

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldNotAddHeaderWhenRequestIsNull() {
        // given
        var context = createTestContextWithoutRequest();

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldForwardIPv4HostAddress() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "192.168.1.100");
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest))
                .thenReturn(UriComponentsBuilder.fromUriString("http://192.168.1.100"));

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verify(spec).header(X_FORWARDED_HOST_HEADER, "192.168.1.100");
        verify(spec).header(X_FORWARDED_PROTO_HEADER, "http");
        verify(spec, never()).header(eq(X_FORWARDED_PORT_HEADER), anyString()); // default HTTP port
    }

    @Test
    void shouldForwardIPv6HostAddress() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "[2001:db8::1]");
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest))
                .thenReturn(UriComponentsBuilder.fromUriString("https://[2001:db8::1]"));

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verify(spec).header(X_FORWARDED_HOST_HEADER, "[2001:db8::1]");
        verify(spec).header(X_FORWARDED_PROTO_HEADER, "https");
        verify(spec, never()).header(eq(X_FORWARDED_PORT_HEADER), anyString()); // default HTTPS port
    }

    @Test
    void shouldUseHostHeaderWhenXForwardedHostMissing() {
        // given
        givenMockRequest.addHeader("Host", "api.example.com");
        // No X-Forwarded-Host header
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest))
                .thenReturn(UriComponentsBuilder.fromUriString("https://api.example.com"));

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verify(spec).header(X_FORWARDED_HOST_HEADER, "api.example.com");
        verify(spec).header(X_FORWARDED_PROTO_HEADER, "https");
        verify(spec, never()).header(eq(X_FORWARDED_PORT_HEADER), anyString());
    }

    @Test
    void shouldPreferXForwardedHostOverHost() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "original.example.com");
        givenMockRequest.addHeader("Host", "proxy.example.com");
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest))
                .thenReturn(UriComponentsBuilder.fromUriString("https://original.example.com"));

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verify(spec).header(X_FORWARDED_HOST_HEADER, "original.example.com");
        verify(spec).header(X_FORWARDED_PROTO_HEADER, "https");
        verify(spec, never()).header(eq(X_FORWARDED_PORT_HEADER), anyString());
    }

    @Test
    void shouldUseHostWithPort() {
        // given
        givenMockRequest.addHeader("Host", "api.example.com:8080");
        // No X-Forwarded-Host header
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest))
                .thenReturn(UriComponentsBuilder.fromUriString("http://api.example.com:8080"));

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verify(spec).header(X_FORWARDED_HOST_HEADER, "api.example.com");
        verify(spec).header(X_FORWARDED_PROTO_HEADER, "http");
        verify(spec).header(X_FORWARDED_PORT_HEADER, "8080");
    }

    @Test
    void shouldUseHostWithIPv4() {
        // given
        givenMockRequest.addHeader("Host", "192.168.1.100");
        // No X-Forwarded-Host header
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest))
                .thenReturn(UriComponentsBuilder.fromUriString("http://192.168.1.100"));

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verify(spec).header(X_FORWARDED_HOST_HEADER, "192.168.1.100");
        verify(spec).header(X_FORWARDED_PROTO_HEADER, "http");
        verify(spec, never()).header(eq(X_FORWARDED_PORT_HEADER), anyString());
    }

    @Test
    void shouldUseHostWithIPv6() {
        // given
        givenMockRequest.addHeader("Host", "[2001:db8::1]");
        // No X-Forwarded-Host header
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest))
                .thenReturn(UriComponentsBuilder.fromUriString("https://[2001:db8::1]"));

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verify(spec).header(X_FORWARDED_HOST_HEADER, "[2001:db8::1]");
        verify(spec).header(X_FORWARDED_PROTO_HEADER, "https");
        verify(spec, never()).header(eq(X_FORWARDED_PORT_HEADER), anyString());
    }

    @Test
    void shouldNotAddHeaderWhenBothMissing() {
        // given
        // No X-Forwarded-Host and no Host headers
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest)).thenReturn(UriComponentsBuilder.newInstance());

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldNotAddHeaderWhenBothBlank() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "   ");
        givenMockRequest.addHeader("Host", "   ");
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest)).thenReturn(UriComponentsBuilder.newInstance());

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    // New tests for port and protocol handling

    @Test
    void shouldSetXForwardedPortForNonDefaultHttpsPort() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "api.example.com");
        givenMockRequest.addHeader(X_FORWARDED_PORT_HEADER, "8443");
        givenMockRequest.addHeader(X_FORWARDED_PROTO_HEADER, "https");
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest))
                .thenReturn(UriComponentsBuilder.fromUriString("https://api.example.com:8443"));

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verify(spec).header(X_FORWARDED_HOST_HEADER, "api.example.com");
        verify(spec).header(X_FORWARDED_PROTO_HEADER, "https");
        verify(spec).header(X_FORWARDED_PORT_HEADER, "8443");
    }

    @Test
    void shouldNotSetXForwardedPortForDefaultHttpPort() {
        // given
        givenMockRequest.addHeader("Host", "api.example.com:80");
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest))
                .thenReturn(UriComponentsBuilder.fromUriString("http://api.example.com"));

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verify(spec).header(X_FORWARDED_HOST_HEADER, "api.example.com");
        verify(spec).header(X_FORWARDED_PROTO_HEADER, "http");
        verify(spec, never()).header(eq(X_FORWARDED_PORT_HEADER), anyString());
    }

    @Test
    void shouldNotSetXForwardedPortForDefaultHttpsPort() {
        // given
        givenMockRequest.addHeader("Host", "api.example.com:443");
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest))
                .thenReturn(UriComponentsBuilder.fromUriString("https://api.example.com"));

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verify(spec).header(X_FORWARDED_HOST_HEADER, "api.example.com");
        verify(spec).header(X_FORWARDED_PROTO_HEADER, "https");
        verify(spec, never()).header(eq(X_FORWARDED_PORT_HEADER), anyString());
    }

    @Test
    void shouldSetXForwardedProtoFromScheme() {
        // given
        givenMockRequest.addHeader("Host", "api.example.com");
        givenMockRequest.setScheme("http");
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest))
                .thenReturn(UriComponentsBuilder.fromUriString("http://api.example.com"));

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verify(spec).header(X_FORWARDED_HOST_HEADER, "api.example.com");
        verify(spec).header(X_FORWARDED_PROTO_HEADER, "http");
        verify(spec, never()).header(eq(X_FORWARDED_PORT_HEADER), anyString());
    }

    @Test
    void shouldHandleAllForwardedHeadersTogether() {
        // given
        givenMockRequest.addHeader(X_FORWARDED_HOST_HEADER, "api.example.com");
        givenMockRequest.addHeader(X_FORWARDED_PROTO_HEADER, "https");
        givenMockRequest.addHeader(X_FORWARDED_PORT_HEADER, "9443");
        var context = createTestContext(givenMockRequest);

        when(xForwardedHostCalculator.hostBuilder(givenMockRequest))
                .thenReturn(UriComponentsBuilder.fromUriString("https://api.example.com:9443"));

        // when
        givenXForwardedHostEnricher.enrich(spec, context);

        // then
        verify(spec).header(X_FORWARDED_HOST_HEADER, "api.example.com");
        verify(spec).header(X_FORWARDED_PROTO_HEADER, "https");
        verify(spec).header(X_FORWARDED_PORT_HEADER, "9443");
    }
}
