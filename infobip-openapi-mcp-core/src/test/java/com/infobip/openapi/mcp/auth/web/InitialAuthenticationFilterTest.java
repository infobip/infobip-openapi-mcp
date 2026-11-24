package com.infobip.openapi.mcp.auth.web;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.auth.ApiAuthFailure;
import com.infobip.openapi.mcp.auth.AuthProperties;
import com.infobip.openapi.mcp.auth.CredentialProvider;
import com.infobip.openapi.mcp.auth.scope.WwwAuthenticateProvider;
import com.infobip.openapi.mcp.enricher.ApiRequestEnricherChain;
import com.infobip.openapi.mcp.error.ErrorModelWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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

    @Mock
    private WwwAuthenticateProvider wwwAuthenticateProvider;

    @Test
    void shouldReturn401WhenCredentialProviderThrows() throws Exception {
        // Given
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var context = new McpRequestContext(request);

        given(authProperties.mode()).willReturn(AuthProperties.AuthMode.ALL_OR_NOTHING);
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
                credentialProviderThatThrows());

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        then(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        verify(filterChain, never()).doFilter(request, response);
    }

    private CredentialProvider credentialProviderThatThrows() {
        return context -> {
            throw new RuntimeException("credential source unavailable");
        };
    }

    @Nested
    class PartialAuthClaudeMode {

        private static final String MCP_RESULT_JSON =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[],\"isError\":true}}";

        @Test
        void shouldLetRequestReachChainWithoutCredentialOrAuthUrlCheck() throws Exception {
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            given(authProperties.mode()).willReturn(AuthProperties.AuthMode.PARTIAL_AUTH_CLAUDE_COMPATIBLE);
            var requestSeenByChain = new AtomicReference<ServletRequest>();
            FilterChain chain = (req, res) -> requestSeenByChain.set(req);

            apiDrivenFilter(true).doFilter(request, response, chain);

            then(requestSeenByChain.get()).isSameAs(request);
            then(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            verifyNoInteractions(restClient, errorModelWriter);
        }

        @Test
        void shouldRemapMarkedUnauthorizedRequestToChallenge() throws Exception {
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            given(authProperties.mode()).willReturn(AuthProperties.AuthMode.PARTIAL_AUTH_CLAUDE_COMPATIBLE);
            given(wwwAuthenticateProvider.buildWwwAuthenticateHeader(request))
                    .willReturn("Bearer resource_metadata=\"http://localhost/.well-known/oauth-protected-resource\"");
            given(errorModelWriter.writeErrorModelAsJson(HttpStatus.UNAUTHORIZED, request, null))
                    .willReturn("{\"error\":\"Unauthorized\"}");

            apiDrivenFilter(true).doFilter(request, response, chainWithMarkedApiAuthFailure(401, MCP_RESULT_JSON));

            then(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            then(response.getHeader("WWW-Authenticate"))
                    .startsWith("Bearer resource_metadata=")
                    .isNotEmpty();
            then(response.getContentAsString()).isEqualTo("{\"error\":\"Unauthorized\"}");
        }

        @Test
        void shouldRemapMarkedForbiddenRequestToScopeErrorChallenge() throws Exception {
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            given(authProperties.mode()).willReturn(AuthProperties.AuthMode.PARTIAL_AUTH_CLAUDE_COMPATIBLE);
            given(wwwAuthenticateProvider.buildWwwAuthenticateHeaderWithScopeError(request))
                    .willReturn("Bearer error=\"insufficient_scope\"");
            given(errorModelWriter.writeErrorModelAsJson(HttpStatus.FORBIDDEN, request, null))
                    .willReturn("{\"error\":\"Forbidden\"}");

            apiDrivenFilter(true).doFilter(request, response, chainWithMarkedApiAuthFailure(403, MCP_RESULT_JSON));

            then(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
            then(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer error=\"insufficient_scope\"");
            then(response.getContentAsString()).isEqualTo("{\"error\":\"Forbidden\"}");
        }

        @Test
        void shouldRemapMarkedRequestEvenWhenChainReplacedTheToolResult() throws Exception {
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            given(authProperties.mode()).willReturn(AuthProperties.AuthMode.PARTIAL_AUTH_CLAUDE_COMPATIBLE);
            given(wwwAuthenticateProvider.buildWwwAuthenticateHeader(request))
                    .willReturn("Bearer resource_metadata=\"http://localhost/.well-known/oauth-protected-resource\"");
            given(errorModelWriter.writeErrorModelAsJson(HttpStatus.UNAUTHORIZED, request, null))
                    .willReturn("{\"error\":\"Unauthorized\"}");
            // e.g. an application-side ToolCallFilter replaced the failed tool result with its own
            // friendly payload: the explicitly configured PARTIAL_AUTH_CLAUDE_COMPATIBLE remapping still prevails
            var replacementPayload = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[],\"isError\":false}}";

            apiDrivenFilter(true).doFilter(request, response, chainWithMarkedApiAuthFailure(401, replacementPayload));

            then(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            then(response.getContentAsString()).isEqualTo("{\"error\":\"Unauthorized\"}");
        }

        @Test
        void shouldPassThroughUnmarkedResponseUnchanged() throws Exception {
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            given(authProperties.mode()).willReturn(AuthProperties.AuthMode.PARTIAL_AUTH_CLAUDE_COMPATIBLE);
            var payload = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[],\"isError\":false}}";
            FilterChain chain = (req, res) -> {
                var httpResponse = (HttpServletResponse) res;
                httpResponse.setStatus(HttpStatus.OK.value());
                httpResponse.setContentType("application/json");
                httpResponse.setHeader("X-Mcp-Custom", "abc");
                httpResponse.getWriter().write(payload);
            };

            apiDrivenFilter(true).doFilter(request, response, chain);

            then(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            then(response.getContentType()).isEqualTo("application/json");
            then(response.getHeader("X-Mcp-Custom")).isEqualTo("abc");
            then(response.getHeader("WWW-Authenticate")).isNull();
            then(response.getContentAsString()).isEqualTo(payload);
            verifyNoInteractions(errorModelWriter);
        }

        @Test
        void shouldNotRemapBasedOnResponseBodyAlone() throws Exception {
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            given(authProperties.mode()).willReturn(AuthProperties.AuthMode.PARTIAL_AUTH_CLAUDE_COMPATIBLE);
            // auth-looking payload bytes mean nothing: only the server-internal request attribute marks
            var payload = """
                    {"jsonrpc":"2.0","id":1,"result":{"content":[],"isError":true,\
                    "_meta":{"auth":{"status":401}}}}""";

            apiDrivenFilter(true).doFilter(request, response, chainWritingJson(payload));

            then(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            then(response.getHeader("WWW-Authenticate")).isNull();
            then(response.getContentAsString()).isEqualTo(payload);
            verifyNoInteractions(errorModelWriter);
        }

        @Test
        void shouldPassThroughNonJsonResponsesUnchanged() throws Exception {
            var request = new MockHttpServletRequest();
            var textResponse = new MockHttpServletResponse();
            given(authProperties.mode()).willReturn(AuthProperties.AuthMode.PARTIAL_AUTH_CLAUDE_COMPATIBLE);
            FilterChain textChain = (req, res) -> {
                var httpResponse = (HttpServletResponse) res;
                httpResponse.setStatus(HttpStatus.OK.value());
                httpResponse.setContentType("text/plain");
                httpResponse.getWriter().write("plain text");
            };

            apiDrivenFilter(true).doFilter(request, textResponse, textChain);

            then(textResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
            then(textResponse.getContentAsString()).isEqualTo("plain text");
            verifyNoInteractions(errorModelWriter);
        }

        @Test
        void shouldNotWrapResponseWhenRemappingDisabled() throws Exception {
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            given(authProperties.mode()).willReturn(AuthProperties.AuthMode.PARTIAL_AUTH_CLAUDE_COMPATIBLE);
            var responseSeenByChain = new AtomicReference<ServletResponse>();
            FilterChain chain = (req, res) -> {
                responseSeenByChain.set(res);
                ((HttpServletRequest) req).setAttribute(ApiAuthFailure.REQUEST_ATTRIBUTE, HttpStatusCode.valueOf(401));
                ((HttpServletResponse) res).getWriter().write("direct");
            };

            apiDrivenFilter(false).doFilter(request, response, chain);

            then(responseSeenByChain.get()).isSameAs(response);
            then(response.getContentAsString()).isEqualTo("direct");
            then(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        private FilterChain chainWithMarkedApiAuthFailure(int status, String body) {
            return (req, res) -> {
                ((HttpServletRequest) req)
                        .setAttribute(ApiAuthFailure.REQUEST_ATTRIBUTE, HttpStatusCode.valueOf(status));
                writeJson((HttpServletResponse) res, body);
            };
        }

        private FilterChain chainWritingJson(String body) {
            return (req, res) -> writeJson((HttpServletResponse) res, body);
        }

        private void writeJson(HttpServletResponse response, String body) throws IOException {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType("application/json");
            response.getWriter().write(body);
        }

        private InitialAuthenticationFilter apiDrivenFilter(boolean remappingEnabled) {
            return new InitialAuthenticationFilter(
                    restClient,
                    authProperties,
                    errorModelWriter,
                    new ApiRequestEnricherChain(List.of()),
                    contextFactory,
                    Optional.of(wwwAuthenticateProvider),
                    Optional.empty(),
                    context -> Optional.empty(),
                    remappingEnabled);
        }
    }

    @Nested
    class PartialAuthOpenAiMode {

        @Test
        void shouldLetRequestReachChainWithoutCredentialOrAuthUrlCheck() throws Exception {
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            given(authProperties.mode()).willReturn(AuthProperties.AuthMode.PARTIAL_AUTH_OPENAI_COMPATIBLE);
            var requestSeenByChain = new AtomicReference<ServletRequest>();
            FilterChain chain = (req, res) -> requestSeenByChain.set(req);

            metaFilter().doFilter(request, response, chain);

            then(requestSeenByChain.get()).isSameAs(request);
            then(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            verifyNoInteractions(restClient, errorModelWriter);
        }

        @Test
        void shouldStayFullyTransparentBecauseChallengesTravelInBand() throws Exception {
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            given(authProperties.mode()).willReturn(AuthProperties.AuthMode.PARTIAL_AUTH_OPENAI_COMPATIBLE);
            FilterChain chain = (req, res) -> {
                // even a stray marker must not trigger remapping in this mode
                ((HttpServletRequest) req).setAttribute(ApiAuthFailure.REQUEST_ATTRIBUTE, HttpStatusCode.valueOf(401));
                ((HttpServletResponse) res).getWriter().write("direct");
            };

            metaFilter().doFilter(request, response, chain);

            then(response.getContentAsString()).isEqualTo("direct");
            then(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            then(response.getHeader("WWW-Authenticate")).isNull();
            verifyNoInteractions(wwwAuthenticateProvider);
        }

        private InitialAuthenticationFilter metaFilter() {
            // as wired by the starter: remapping is only ever enabled for PARTIAL_AUTH_CLAUDE_COMPATIBLE
            return new InitialAuthenticationFilter(
                    restClient,
                    authProperties,
                    errorModelWriter,
                    new ApiRequestEnricherChain(List.of()),
                    contextFactory,
                    Optional.of(wwwAuthenticateProvider),
                    Optional.empty(),
                    context -> Optional.empty(),
                    false);
        }
    }
}
