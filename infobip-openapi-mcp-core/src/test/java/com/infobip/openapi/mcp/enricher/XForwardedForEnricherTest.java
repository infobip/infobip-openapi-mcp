package com.infobip.openapi.mcp.enricher;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.*;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.util.XForwardedForCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class XForwardedForEnricherTest {

    private final XForwardedForEnricher xForwardedForEnricher =
            new XForwardedForEnricher(new XForwardedForCalculator());

    @Mock
    private RestClient.RequestHeadersSpec<?> spec;

    private McpRequestContext createTestContext(MockHttpServletRequest request) {
        return new McpRequestContext(request);
    }

    private McpRequestContext createTestContextWithoutRequest() {
        return new McpRequestContext();
    }

    @Test
    void shouldAddXForwardedForHeaderWhenPresent() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("203.0.113.195");
        var context = createTestContext(mockRequest);

        // when
        xForwardedForEnricher.enrich(spec, context);

        // then
        var headerCaptor = ArgumentCaptor.forClass(String.class);
        var valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(headerCaptor.capture(), valueCaptor.capture());

        then(headerCaptor.getValue()).isEqualTo("X-Forwarded-For");
        then(valueCaptor.getValue()).isEqualTo("203.0.113.195");
    }

    @Test
    void shouldAddXForwardedForHeaderWithMultipleIPs() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Forwarded-For", "203.0.113.195, 198.51.100.1");
        mockRequest.setRemoteAddr("198.51.100.2");
        var context = createTestContext(mockRequest);

        // when
        xForwardedForEnricher.enrich(spec, context);

        // then
        var valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(org.mockito.ArgumentMatchers.eq("X-Forwarded-For"), valueCaptor.capture());
        then(valueCaptor.getValue()).isEqualTo("203.0.113.195, 198.51.100.1, 198.51.100.2");
    }

    @Test
    void shouldNotAddHeaderWhenXForwardedForIsNull() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr(null);
        // No X-Forwarded-For header and no remote address set
        var context = createTestContext(mockRequest);

        // when
        xForwardedForEnricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldNotAddHeaderWhenXForwardedForIsEmpty() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Forwarded-For", "");
        mockRequest.setRemoteAddr("");
        var context = createTestContext(mockRequest);

        // when
        xForwardedForEnricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldNotAddHeaderWhenXForwardedForIsBlank() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Forwarded-For", "   ");
        mockRequest.setRemoteAddr("   ");
        var context = createTestContext(mockRequest);

        // when
        xForwardedForEnricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldNotAddHeaderWhenRequestIsNull() {
        // given
        var context = createTestContextWithoutRequest();

        // when
        xForwardedForEnricher.enrich(spec, context);

        // then
        verifyNoInteractions(spec);
    }

    @Test
    void shouldHandleIPv6Address() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("2001:db8:85a3:8d3:1319:8a2e:370:7348");
        var context = createTestContext(mockRequest);

        // when
        xForwardedForEnricher.enrich(spec, context);

        // then
        var valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(org.mockito.ArgumentMatchers.eq("X-Forwarded-For"), valueCaptor.capture());
        then(valueCaptor.getValue()).isEqualTo("2001:db8:85a3:8d3:1319:8a2e:370:7348");
    }
}
