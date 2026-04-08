package com.infobip.openapi.mcp.auth.web;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.AuthProperties;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class InitialAuthenticationFilterTest {

    @Mock
    private RestClient restClient;

    @Mock
    private AuthProperties authProperties;

    @Mock
    private ErrorModelWriter errorModelWriter;

    @Mock
    private McpRequestContextFactory contextFactory;

    @Mock
    private FilterChain filterChain;

    @Test
    void shouldReturn401WhenCredentialProviderThrows() throws Exception {
        // Given
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var context = new McpRequestContext(request);

        given(contextFactory.forServletFilter(request)).willReturn(context);
        given(errorModelWriter.writeErrorModelAsJson(HttpStatus.UNAUTHORIZED, request, null))
                .willReturn("{\"requestError\":{\"serviceException\":{\"messageId\":\"UNAUTHORIZED\"}}}");

        var filter = new InitialAuthenticationFilter(
                restClient,
                authProperties,
                errorModelWriter,
                new ApiRequestEnricherChain(List.of()),
                contextFactory,
                Optional.empty(),
                Optional.empty(),
                context2 -> {
                    throw new RuntimeException("credential source unavailable");
                });

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        then(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        verify(filterChain, never()).doFilter(request, response);
    }
}
