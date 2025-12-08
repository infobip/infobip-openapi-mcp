package com.infobip.openapi.mcp.enricher;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.infobip.openapi.mcp.McpRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class XForwardedHostEnricherTest {

    @Mock
    private RestClient.RequestHeadersSpec<?> spec;

    private McpRequestContext createTestContext(MockHttpServletRequest request) {
        return new McpRequestContext(request);
    }

    private McpRequestContext createTestContextWithoutRequest() {
        return new McpRequestContext();
    }

    @Test
    void shouldForwardXForwardedHostHeaderWhenPresent() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Forwarded-Host", "api.example.com");
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        var headerCaptor = ArgumentCaptor.forClass(String.class);
        var valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(headerCaptor.capture(), valueCaptor.capture());

        then(headerCaptor.getValue()).isEqualTo("X-Forwarded-Host");
        then(valueCaptor.getValue()).isEqualTo("api.example.com");
    }

    @Test
    void shouldForwardComplexHostValue() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Forwarded-Host", "subdomain.api.example.com:8080");
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        var valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(org.mockito.ArgumentMatchers.eq("X-Forwarded-Host"), valueCaptor.capture());
        then(valueCaptor.getValue()).isEqualTo("subdomain.api.example.com:8080");
    }

    @Test
    void shouldNotAddHeaderWhenXForwardedHostIsMissing() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        // No X-Forwarded-Host header added
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldNotAddHeaderWhenXForwardedHostIsEmpty() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Forwarded-Host", "");
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldNotAddHeaderWhenXForwardedHostIsBlank() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Forwarded-Host", "   ");
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldNotAddHeaderWhenRequestIsNull() {
        // given
        var enricher = new XForwardedHostEnricher();
        var context = createTestContextWithoutRequest();

        // when
        enricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldForwardIPv4HostAddress() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Forwarded-Host", "192.168.1.100");
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        var valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(org.mockito.ArgumentMatchers.eq("X-Forwarded-Host"), valueCaptor.capture());
        then(valueCaptor.getValue()).isEqualTo("192.168.1.100");
    }

    @Test
    void shouldForwardIPv6HostAddress() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Forwarded-Host", "[2001:db8::1]");
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        var valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(org.mockito.ArgumentMatchers.eq("X-Forwarded-Host"), valueCaptor.capture());
        then(valueCaptor.getValue()).isEqualTo("[2001:db8::1]");
    }

    @Test
    void shouldUseHostHeaderWhenXForwardedHostMissing() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("Host", "api.example.com");
        // No X-Forwarded-Host header
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        var headerCaptor = ArgumentCaptor.forClass(String.class);
        var valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(headerCaptor.capture(), valueCaptor.capture());

        then(headerCaptor.getValue()).isEqualTo("X-Forwarded-Host");
        then(valueCaptor.getValue()).isEqualTo("api.example.com");
    }

    @Test
    void shouldPreferXForwardedHostOverHost() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Forwarded-Host", "original.example.com");
        mockRequest.addHeader("Host", "proxy.example.com");
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        var valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(org.mockito.ArgumentMatchers.eq("X-Forwarded-Host"), valueCaptor.capture());
        then(valueCaptor.getValue()).isEqualTo("original.example.com");
    }

    @Test
    void shouldUseHostWithPort() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("Host", "api.example.com:8080");
        // No X-Forwarded-Host header
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        var valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(org.mockito.ArgumentMatchers.eq("X-Forwarded-Host"), valueCaptor.capture());
        then(valueCaptor.getValue()).isEqualTo("api.example.com:8080");
    }

    @Test
    void shouldUseHostWithIPv4() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("Host", "192.168.1.100");
        // No X-Forwarded-Host header
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        var valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(org.mockito.ArgumentMatchers.eq("X-Forwarded-Host"), valueCaptor.capture());
        then(valueCaptor.getValue()).isEqualTo("192.168.1.100");
    }

    @Test
    void shouldUseHostWithIPv6() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("Host", "[2001:db8::1]");
        // No X-Forwarded-Host header
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        var valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(org.mockito.ArgumentMatchers.eq("X-Forwarded-Host"), valueCaptor.capture());
        then(valueCaptor.getValue()).isEqualTo("[2001:db8::1]");
    }

    @Test
    void shouldNotAddHeaderWhenBothMissing() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        // No X-Forwarded-Host and no Host headers
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldNotAddHeaderWhenBothBlank() {
        // given
        var enricher = new XForwardedHostEnricher();
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Forwarded-Host", "   ");
        mockRequest.addHeader("Host", "   ");
        var context = createTestContext(mockRequest);

        // when
        enricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }
}
