package com.infobip.openapi.mcp.enricher;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class UserAgentEnricherTest {

    @Mock
    private RestClient.RequestHeadersSpec<?> spec;

    private McpRequestContext createTestContext() {
        return new McpRequestContext();
    }

    @Test
    void shouldAddUserAgentWhenConfigured() {
        // given
        var properties = createProperties("my-custom-user-agent");
        var enricher = new UserAgentEnricher(properties);
        var context = createTestContext();

        // when
        enricher.enrich(spec, context);

        // then
        var headerNameCaptor = ArgumentCaptor.forClass(String.class);
        var headerValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(headerNameCaptor.capture(), headerValueCaptor.capture());
        then(headerNameCaptor.getValue()).isEqualTo(HttpHeaders.USER_AGENT);
        then(headerValueCaptor.getValue()).isEqualTo("my-custom-user-agent");
    }

    @Test
    void shouldUseDefaultUserAgent() {
        // given
        var properties = createProperties(null);
        var enricher = new UserAgentEnricher(properties);
        var context = createTestContext();

        // when
        enricher.enrich(spec, context);

        // then
        var headerNameCaptor = ArgumentCaptor.forClass(String.class);
        var headerValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(headerNameCaptor.capture(), headerValueCaptor.capture());
        then(headerNameCaptor.getValue()).isEqualTo(HttpHeaders.USER_AGENT);
        then(headerValueCaptor.getValue()).isEqualTo("openapi-mcp");
    }

    @Test
    void shouldNotAddUserAgentWhenEmpty() {
        // given
        var properties = createProperties("");
        var enricher = new UserAgentEnricher(properties);
        var context = createTestContext();

        // when
        enricher.enrich(spec, context);

        // then
        verify(spec, never()).header(eq(HttpHeaders.USER_AGENT), anyString());
    }

    @Test
    void shouldNotAddUserAgentWhenBlank() {
        // given
        var properties = createProperties("   ");
        var enricher = new UserAgentEnricher(properties);
        var context = createTestContext();

        // when
        enricher.enrich(spec, context);

        // then
        verify(spec, never()).header(eq(HttpHeaders.USER_AGENT), anyString());
    }

    @Test
    void shouldHandleComplexUserAgentString() {
        // given
        var complexUserAgent = "Mozilla/5.0 (compatible; MyBot/1.0; +https://example.com/bot)";
        var properties = createProperties(complexUserAgent);
        var enricher = new UserAgentEnricher(properties);
        var context = createTestContext();

        // when
        enricher.enrich(spec, context);

        // then
        var headerValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(spec).header(eq(HttpHeaders.USER_AGENT), headerValueCaptor.capture());
        then(headerValueCaptor.getValue()).isEqualTo(complexUserAgent);
    }

    private OpenApiMcpProperties createProperties(String userAgent) {
        return new OpenApiMcpProperties(
                URI.create("https://example.com/openapi.json"),
                "https://example.com/api",
                null,
                null,
                userAgent,
                null,
                null);
    }
}
