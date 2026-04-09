package com.infobip.openapi.mcp.auth;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

import com.infobip.openapi.mcp.McpRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

@ExtendWith(MockitoExtension.class)
class HttpServletRequestCredentialProviderTest {

    @InjectMocks
    private HttpServletRequestCredentialProvider provider;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Test
    void shouldReturnEmptyWhenHttpServletRequestIsNull() {
        // Given
        var context = new McpRequestContext();

        // When
        var result = provider.provide(context);

        // Then
        then(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenAuthorizationHeaderIsAbsent() {
        // Given
        given(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).willReturn(null);
        var context = new McpRequestContext(httpServletRequest);

        // When
        var result = provider.provide(context);

        // Then
        then(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenAuthorizationHeaderIsBlank() {
        // Given
        given(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).willReturn("   ");
        var context = new McpRequestContext(httpServletRequest);

        // When
        var result = provider.provide(context);

        // Then
        then(result).isEmpty();
    }

    @Test
    void shouldReturnAuthorizationHeaderValue() {
        // Given
        var givenAuthHeader = "Bearer eyJhbGciOiJIUzI1NiJ9.test.signature";
        given(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).willReturn(givenAuthHeader);
        var context = new McpRequestContext(httpServletRequest);

        // When
        var result = provider.provide(context);

        // Then
        then(result).contains(givenAuthHeader);
    }
}
