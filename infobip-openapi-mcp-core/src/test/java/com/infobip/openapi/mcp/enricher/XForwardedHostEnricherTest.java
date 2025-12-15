//package com.infobip.openapi.mcp.enricher;
//
//import com.infobip.openapi.mcp.McpRequestContext;
//import com.infobip.openapi.mcp.util.XForwardedHostCalculator;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.BDDMockito;
//import org.mockito.Mock;
//import org.mockito.Mockito;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.mock.web.MockHttpServletRequest;
//import org.springframework.web.client.RestClient;
//import org.springframework.web.util.UriComponentsBuilder;
//
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class XForwardedHostEnricherTest {
//
//    @Mock
//    private RestClient.RequestHeadersSpec<?> spec;
//
//    @Mock
//    private XForwardedHostCalculator xForwardedHostCalculator;
//
//    @BeforeEach
//    void setUp() {
//        // Enable method chaining for the spec mock (lenient for tests that don't use it)
//        //lenient().doReturn(spec).when(spec).header(anyString(), anyString());
//        BDDMockito.reset(xForwardedHostCalculator);
//    }
//
//    private McpRequestContext createTestContext(MockHttpServletRequest request) {
//        return new McpRequestContext(request, null, null, null);
//    }
//
//    private McpRequestContext createTestContextWithoutRequest() {
//        return new McpRequestContext(null, null, null, null);
//    }
//
//    @Test
//    void shouldForwardXForwardedHostHeaderWhenPresent() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("X-Forwarded-Host", "api.example.com");
//        var context = createTestContext(mockRequest);
//
//        given(xForwardedHostCalculator.hostBuilder(mockRequest))
//                        .willReturn(UriComponentsBuilder.fromUriString("https://api.example.com"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "api.example.com");
//        verify(spec).header("X-Forwarded-Proto", "https");
//        verify(spec, never()).header(eq("X-Forwarded-Port"), anyString()); // default HTTPS port
//    }
//
//    @Test
//    void shouldForwardComplexHostValue() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("X-Forwarded-Host", "subdomain.api.example.com:8080");
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("https://subdomain.api.example.com:8080"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "subdomain.api.example.com");
//        verify(spec).header("X-Forwarded-Proto", "https");
//        verify(spec).header("X-Forwarded-Port", "8080");
//    }
//
//    @Test
//    void shouldNotAddHeaderWhenXForwardedHostIsMissing() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        // No X-Forwarded-Host header added
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.newInstance());
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verifyNoInteractions(spec);
//    }
//
//    @Test
//    void shouldNotAddHeaderWhenXForwardedHostIsEmpty() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("X-Forwarded-Host", "");
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.newInstance());
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verifyNoInteractions(spec);
//    }
//
//    @Test
//    void shouldNotAddHeaderWhenXForwardedHostIsBlank() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("X-Forwarded-Host", "   ");
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.newInstance());
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verifyNoInteractions(spec);
//    }
//
//    @Test
//    void shouldNotAddHeaderWhenRequestIsNull() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var context = createTestContextWithoutRequest();
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verifyNoInteractions(spec);
//    }
//
//    @Test
//    void shouldForwardIPv4HostAddress() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("X-Forwarded-Host", "192.168.1.100");
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("http://192.168.1.100"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "192.168.1.100");
//        verify(spec).header("X-Forwarded-Proto", "http");
//        verify(spec, never()).header(eq("X-Forwarded-Port"), anyString()); // default HTTP port
//    }
//
//    @Test
//    void shouldForwardIPv6HostAddress() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("X-Forwarded-Host", "[2001:db8::1]");
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("https://[2001:db8::1]"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "[2001:db8::1]");
//        verify(spec).header("X-Forwarded-Proto", "https");
//        verify(spec, never()).header(eq("X-Forwarded-Port"), anyString()); // default HTTPS port
//    }
//
//    @Test
//    void shouldUseHostHeaderWhenXForwardedHostMissing() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("Host", "api.example.com");
//        // No X-Forwarded-Host header
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("https://api.example.com"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "api.example.com");
//        verify(spec).header("X-Forwarded-Proto", "https");
//        verify(spec, never()).header(eq("X-Forwarded-Port"), anyString());
//    }
//
//    @Test
//    void shouldPreferXForwardedHostOverHost() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("X-Forwarded-Host", "original.example.com");
//        mockRequest.addHeader("Host", "proxy.example.com");
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("https://original.example.com"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "original.example.com");
//        verify(spec).header("X-Forwarded-Proto", "https");
//        verify(spec, never()).header(eq("X-Forwarded-Port"), anyString());
//    }
//
//    @Test
//    void shouldUseHostWithPort() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("Host", "api.example.com:8080");
//        // No X-Forwarded-Host header
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("http://api.example.com:8080"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "api.example.com");
//        verify(spec).header("X-Forwarded-Proto", "http");
//        verify(spec).header("X-Forwarded-Port", "8080");
//    }
//
//    @Test
//    void shouldUseHostWithIPv4() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("Host", "192.168.1.100");
//        // No X-Forwarded-Host header
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("http://192.168.1.100"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "192.168.1.100");
//        verify(spec).header("X-Forwarded-Proto", "http");
//        verify(spec, never()).header(eq("X-Forwarded-Port"), anyString());
//    }
//
//    @Test
//    void shouldUseHostWithIPv6() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("Host", "[2001:db8::1]");
//        // No X-Forwarded-Host header
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("https://[2001:db8::1]"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "[2001:db8::1]");
//        verify(spec).header("X-Forwarded-Proto", "https");
//        verify(spec, never()).header(eq("X-Forwarded-Port"), anyString());
//    }
//
//    @Test
//    void shouldNotAddHeaderWhenBothMissing() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        // No X-Forwarded-Host and no Host headers
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.newInstance());
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verifyNoInteractions(spec);
//    }
//
//    @Test
//    void shouldNotAddHeaderWhenBothBlank() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("X-Forwarded-Host", "   ");
//        mockRequest.addHeader("Host", "   ");
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.newInstance());
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verifyNoInteractions(spec);
//    }
//
//    // New tests for port and protocol handling
//
//    @Test
//    void shouldSetXForwardedPortForNonDefaultHttpsPort() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("X-Forwarded-Host", "api.example.com");
//        mockRequest.addHeader("X-Forwarded-Port", "8443");
//        mockRequest.addHeader("X-Forwarded-Proto", "https");
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("https://api.example.com:8443"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "api.example.com");
//        verify(spec).header("X-Forwarded-Proto", "https");
//        verify(spec).header("X-Forwarded-Port", "8443");
//    }
//
//    @Test
//    void shouldNotSetXForwardedPortForDefaultHttpPort() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("Host", "api.example.com:80");
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("http://api.example.com"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "api.example.com");
//        verify(spec).header("X-Forwarded-Proto", "http");
//        verify(spec, never()).header(eq("X-Forwarded-Port"), anyString());
//    }
//
//    @Test
//    void shouldNotSetXForwardedPortForDefaultHttpsPort() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("Host", "api.example.com:443");
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("https://api.example.com"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "api.example.com");
//        verify(spec).header("X-Forwarded-Proto", "https");
//        verify(spec, never()).header(eq("X-Forwarded-Port"), anyString());
//    }
//
//    @Test
//    void shouldSetXForwardedProtoFromScheme() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("Host", "api.example.com");
//        mockRequest.setScheme("http");
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("http://api.example.com"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "api.example.com");
//        verify(spec).header("X-Forwarded-Proto", "http");
//        verify(spec, never()).header(eq("X-Forwarded-Port"), anyString());
//    }
//
//    @Test
//    void shouldHandleAllForwardedHeadersTogether() {
//        // given
//        var enricher = new XForwardedHostEnricher(xForwardedHostCalculator);
//        var mockRequest = new MockHttpServletRequest();
//        mockRequest.addHeader("X-Forwarded-Host", "api.example.com");
//        mockRequest.addHeader("X-Forwarded-Proto", "https");
//        mockRequest.addHeader("X-Forwarded-Port", "9443");
//        var context = createTestContext(mockRequest);
//
//        when(xForwardedHostCalculator.hostBuilder(mockRequest))
//                .thenReturn(UriComponentsBuilder.fromHttpUrl("https://api.example.com:9443"));
//
//        // when
//        enricher.enrich(spec, context);
//
//        // then
//        verify(spec).header("X-Forwarded-Host", "api.example.com");
//        verify(spec).header("X-Forwarded-Proto", "https");
//        verify(spec).header("X-Forwarded-Port", "9443");
//    }
//}
