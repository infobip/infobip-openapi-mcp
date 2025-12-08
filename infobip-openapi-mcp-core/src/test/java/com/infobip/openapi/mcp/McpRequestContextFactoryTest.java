package com.infobip.openapi.mcp;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class McpRequestContextFactoryTest {

    @Mock
    private McpSyncServerExchange exchange;

    @Mock
    private McpSchema.Implementation clientInfo;

    private McpRequestContextFactory factory;

    @BeforeEach
    void setUp() {
        factory = new McpRequestContextFactory();
    }

    @Test
    void shouldCreateContextFromStatefulTransportWithAllFields() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("192.0.2.100");
        var requestAttributes = new ServletRequestAttributes(mockRequest);
        var sessionId = "session-123-abc";

        given(exchange.sessionId()).willReturn(sessionId);
        given(exchange.getClientInfo()).willReturn(clientInfo);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder.when(RequestContextHolder::currentRequestAttributes).thenReturn(requestAttributes);

            // when
            var context = factory.forStatefulTransport(exchange, null, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isEqualTo(mockRequest);
            then(context.sessionId()).isEqualTo(sessionId);
            then(context.clientInfo()).isEqualTo(clientInfo);
            then(context.toolName()).isNull();
        }
    }

    @Test
    void shouldCreateContextFromStatefulTransportWithoutHttpRequest() {
        // given
        var sessionId = "session-456-def";
        given(exchange.sessionId()).willReturn(sessionId);
        given(exchange.getClientInfo()).willReturn(clientInfo);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder
                    .when(RequestContextHolder::currentRequestAttributes)
                    .thenThrow(new IllegalStateException("No request context available"));

            // when
            var context = factory.forStatefulTransport(exchange, null, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isNull();
            then(context.sessionId()).isEqualTo(sessionId);
            then(context.clientInfo()).isEqualTo(clientInfo);
            then(context.toolName()).isNull();
        }
    }

    @Test
    void shouldCreateContextFromStatefulTransportWithNullClientInfo() {
        // given
        var sessionId = "session-789-ghi";
        given(exchange.sessionId()).willReturn(sessionId);
        given(exchange.getClientInfo()).willReturn(null);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder
                    .when(RequestContextHolder::currentRequestAttributes)
                    .thenThrow(new IllegalStateException("No request context available"));

            // when
            var context = factory.forStatefulTransport(exchange, null, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isNull();
            then(context.sessionId()).isEqualTo(sessionId);
            then(context.clientInfo()).isNull();
            then(context.toolName()).isNull();
        }
    }

    @Test
    void shouldCreateContextFromStatefulTransportWithNullSessionId() {
        // given
        given(exchange.sessionId()).willReturn(null);
        given(exchange.getClientInfo()).willReturn(clientInfo);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder
                    .when(RequestContextHolder::currentRequestAttributes)
                    .thenThrow(new IllegalStateException("No request context available"));

            // when
            var context = factory.forStatefulTransport(exchange, null, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isNull();
            then(context.sessionId()).isNull();
            then(context.clientInfo()).isEqualTo(clientInfo);
        }
    }

    @Test
    void shouldCreateContextFromStatelessTransport() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("198.51.100.50");
        var requestAttributes = new ServletRequestAttributes(mockRequest);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder.when(RequestContextHolder::currentRequestAttributes).thenReturn(requestAttributes);

            // when
            var context = factory.forStatelessTransport(null, null, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isEqualTo(mockRequest);
            then(context.sessionId()).isNull();
            then(context.clientInfo()).isNull();
        }
    }

    @Test
    void shouldCreateContextFromStatelessTransportWithoutHttpRequest() {
        // given
        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder
                    .when(RequestContextHolder::currentRequestAttributes)
                    .thenThrow(new IllegalStateException("No request context available"));

            // when
            var context = factory.forStatelessTransport(null, null, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isNull();
            then(context.sessionId()).isNull();
            then(context.clientInfo()).isNull();
        }
    }

    @Test
    void shouldCreateContextFromStatelessTransportWithTransportContext() {
        // given
        var transportContext = mock(McpTransportContext.class);
        var mockRequest = new MockHttpServletRequest();
        var requestAttributes = new ServletRequestAttributes(mockRequest);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder.when(RequestContextHolder::currentRequestAttributes).thenReturn(requestAttributes);

            // when
            var context = factory.forStatelessTransport(transportContext, null, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isEqualTo(mockRequest);
            then(context.sessionId()).isNull();
            then(context.clientInfo()).isNull();
        }
    }

    @Test
    void shouldHandleNonServletRequestAttributes() {
        // given
        var sessionId = "session-non-servlet";
        given(exchange.sessionId()).willReturn(sessionId);
        given(exchange.getClientInfo()).willReturn(clientInfo);

        // Mock non-ServletRequestAttributes
        var nonServletAttributes = mock(org.springframework.web.context.request.RequestAttributes.class);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder.when(RequestContextHolder::currentRequestAttributes).thenReturn(nonServletAttributes);

            // when
            var context = factory.forStatefulTransport(exchange, null, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isNull();
            then(context.sessionId()).isEqualTo(sessionId);
            then(context.clientInfo()).isEqualTo(clientInfo);
        }
    }

    @Test
    void shouldCreateContextWithCompleteClientInfo() {
        // given
        var mockRequest = new MockHttpServletRequest();
        var requestAttributes = new ServletRequestAttributes(mockRequest);
        var sessionId = "session-complete";

        given(exchange.sessionId()).willReturn(sessionId);
        given(exchange.getClientInfo()).willReturn(clientInfo);
        given(clientInfo.name()).willReturn("Claude Desktop");
        given(clientInfo.version()).willReturn("2025-06-18");

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder.when(RequestContextHolder::currentRequestAttributes).thenReturn(requestAttributes);

            // when
            var context = factory.forStatefulTransport(exchange, null, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isEqualTo(mockRequest);
            then(context.sessionId()).isEqualTo(sessionId);
            then(context.clientInfo()).isEqualTo(clientInfo);
            then(context.clientInfo().name()).isEqualTo("Claude Desktop");
            then(context.clientInfo().version()).isEqualTo("2025-06-18");
        }
    }

    @Test
    void shouldHandleHttpRequestWithHeaders() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("Authorization", "Bearer token123");
        mockRequest.addHeader("User-Agent", "Test-Agent/1.0");
        mockRequest.setRemoteAddr("203.0.113.1");
        var requestAttributes = new ServletRequestAttributes(mockRequest);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder.when(RequestContextHolder::currentRequestAttributes).thenReturn(requestAttributes);

            // when
            var context = factory.forStatelessTransport(null, null, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isNotNull();
            then(context.httpServletRequest().getHeader("Authorization")).isEqualTo("Bearer token123");
            then(context.httpServletRequest().getHeader("User-Agent")).isEqualTo("Test-Agent/1.0");
            then(context.httpServletRequest().getRemoteAddr()).isEqualTo("203.0.113.1");
        }
    }

    @Test
    void shouldCreateContextFromServletFilter() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("192.0.2.200");
        mockRequest.addHeader("X-Forwarded-For", "203.0.113.5");
        mockRequest.addHeader("Authorization", "Bearer filter-token");

        // when
        var context = factory.forServletFilter(mockRequest);

        // then
        then(context).isNotNull();
        then(context.httpServletRequest()).isEqualTo(mockRequest);
        then(context.sessionId()).isNull();
        then(context.clientInfo()).isNull();
        then(context.httpServletRequest().getRemoteAddr()).isEqualTo("192.0.2.200");
        then(context.httpServletRequest().getHeader("X-Forwarded-For")).isEqualTo("203.0.113.5");
        then(context.httpServletRequest().getHeader("Authorization")).isEqualTo("Bearer filter-token");
    }

    @Test
    void shouldCreateContextFromServletFilterWithoutRequestContextHolder() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("198.51.100.75");

        // when
        // This should work without accessing RequestContextHolder at all
        var context = factory.forServletFilter(mockRequest);

        // then
        then(context).isNotNull();
        then(context.httpServletRequest()).isEqualTo(mockRequest);
        then(context.sessionId()).isNull();
        then(context.clientInfo()).isNull();
        // Verify no interaction with RequestContextHolder by confirming the request is directly used
        then(context.httpServletRequest().getRemoteAddr()).isEqualTo("198.51.100.75");
    }

    @Test
    void shouldCreateContextFromStatefulTransportWithToolName() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("192.0.2.100");
        var requestAttributes = new ServletRequestAttributes(mockRequest);
        var sessionId = "session-123-abc";
        var toolName = "tool_name";

        given(exchange.sessionId()).willReturn(sessionId);
        given(exchange.getClientInfo()).willReturn(clientInfo);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder.when(RequestContextHolder::currentRequestAttributes).thenReturn(requestAttributes);

            // when
            var context = factory.forStatefulTransport(exchange, toolName, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isEqualTo(mockRequest);
            then(context.sessionId()).isEqualTo(sessionId);
            then(context.clientInfo()).isEqualTo(clientInfo);
            then(context.toolName()).isEqualTo(toolName);
        }
    }

    @Test
    void shouldCreateContextFromStatefulTransportWithToolNameAndNoHttpRequest() {
        // given
        var sessionId = "session-456-def";
        var toolName = "tool_name";
        given(exchange.sessionId()).willReturn(sessionId);
        given(exchange.getClientInfo()).willReturn(clientInfo);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder
                    .when(RequestContextHolder::currentRequestAttributes)
                    .thenThrow(new IllegalStateException("No request context available"));

            // when
            var context = factory.forStatefulTransport(exchange, toolName, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isNull();
            then(context.sessionId()).isEqualTo(sessionId);
            then(context.clientInfo()).isEqualTo(clientInfo);
            then(context.toolName()).isEqualTo(toolName);
        }
    }

    @Test
    void shouldCreateContextFromStatefulTransportWithToolNameAndNullClientInfo() {
        // given
        var sessionId = "session-789-ghi";
        var toolName = "some_tool_name";
        given(exchange.sessionId()).willReturn(sessionId);
        given(exchange.getClientInfo()).willReturn(null);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder
                    .when(RequestContextHolder::currentRequestAttributes)
                    .thenThrow(new IllegalStateException("No request context available"));

            // when
            var context = factory.forStatefulTransport(exchange, toolName, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isNull();
            then(context.sessionId()).isEqualTo(sessionId);
            then(context.clientInfo()).isNull();
            then(context.toolName()).isEqualTo(toolName);
        }
    }

    @Test
    void shouldCreateContextFromStatefulTransportWithToolNameAndNullSessionId() {
        // given
        var toolName = "list_items";
        given(exchange.sessionId()).willReturn(null);
        given(exchange.getClientInfo()).willReturn(clientInfo);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder
                    .when(RequestContextHolder::currentRequestAttributes)
                    .thenThrow(new IllegalStateException("No request context available"));

            // when
            var context = factory.forStatefulTransport(exchange, toolName, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isNull();
            then(context.sessionId()).isNull();
            then(context.clientInfo()).isEqualTo(clientInfo);
            then(context.toolName()).isEqualTo(toolName);
        }
    }

    @Test
    void shouldCreateContextFromStatelessTransportWithToolName() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("198.51.100.50");
        var requestAttributes = new ServletRequestAttributes(mockRequest);
        var toolName = "some_action";

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder.when(RequestContextHolder::currentRequestAttributes).thenReturn(requestAttributes);

            // when
            var context = factory.forStatelessTransport(null, toolName, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isEqualTo(mockRequest);
            then(context.sessionId()).isNull();
            then(context.clientInfo()).isNull();
            then(context.toolName()).isEqualTo(toolName);
        }
    }

    @Test
    void shouldCreateContextFromStatelessTransportWithToolNameAndNoHttpRequest() {
        // given
        var toolName = "create_order";

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder
                    .when(RequestContextHolder::currentRequestAttributes)
                    .thenThrow(new IllegalStateException("No request context available"));

            // when
            var context = factory.forStatelessTransport(null, toolName, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isNull();
            then(context.sessionId()).isNull();
            then(context.clientInfo()).isNull();
            then(context.toolName()).isEqualTo(toolName);
        }
    }

    @Test
    void shouldCreateContextFromStatelessTransportWithToolNameAndTransportContext() {
        // given
        var transportContext = mock(McpTransportContext.class);
        var mockRequest = new MockHttpServletRequest();
        var requestAttributes = new ServletRequestAttributes(mockRequest);
        var toolName = "update_user_profile";

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder.when(RequestContextHolder::currentRequestAttributes).thenReturn(requestAttributes);

            // when
            var context = factory.forStatelessTransport(transportContext, toolName, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isEqualTo(mockRequest);
            then(context.sessionId()).isNull();
            then(context.clientInfo()).isNull();
            then(context.toolName()).isEqualTo(toolName);
        }
    }

    @Test
    void shouldCreateContextWithAllFieldsIncludingToolName() {
        // given
        var mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("203.0.113.100");
        mockRequest.addHeader("User-Agent", "Test-Agent/1.0");
        var requestAttributes = new ServletRequestAttributes(mockRequest);
        var sessionId = "session-complete-with-tool";
        var toolName = "post_messages";
        var fullOperation = givenOperation();

        given(exchange.sessionId()).willReturn(sessionId);
        given(exchange.getClientInfo()).willReturn(clientInfo);
        given(clientInfo.name()).willReturn("Claude Desktop");
        given(clientInfo.version()).willReturn("2025-06-18");

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder.when(RequestContextHolder::currentRequestAttributes).thenReturn(requestAttributes);

            // when
            var context = factory.forStatefulTransport(exchange, toolName, fullOperation);

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isEqualTo(mockRequest);
            then(context.sessionId()).isEqualTo(sessionId);
            then(context.clientInfo()).isEqualTo(clientInfo);
            then(context.clientInfo().name()).isEqualTo("Claude Desktop");
            then(context.clientInfo().version()).isEqualTo("2025-06-18");
            then(context.openApiOperation()).isEqualTo(fullOperation);
            then(context.toolName()).isEqualTo(toolName);
        }
    }

    @Test
    void shouldHandleEmptyToolName() {
        // given
        var mockRequest = new MockHttpServletRequest();
        var requestAttributes = new ServletRequestAttributes(mockRequest);
        var sessionId = "session-empty-tool";
        var toolName = "";

        given(exchange.sessionId()).willReturn(sessionId);
        given(exchange.getClientInfo()).willReturn(clientInfo);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder.when(RequestContextHolder::currentRequestAttributes).thenReturn(requestAttributes);

            // when
            var context = factory.forStatefulTransport(exchange, toolName, givenOperation());

            // then
            then(context).isNotNull();
            then(context.httpServletRequest()).isEqualTo(mockRequest);
            then(context.sessionId()).isEqualTo(sessionId);
            then(context.clientInfo()).isEqualTo(clientInfo);
            then(context.toolName()).isEqualTo(toolName);
        }
    }

    @Test
    void shouldHandleToolNameWithSpecialCharacters() {
        // given
        var toolName = "post_sms_with-special_chars_123";
        var sessionId = "session-special-chars";

        given(exchange.sessionId()).willReturn(sessionId);
        given(exchange.getClientInfo()).willReturn(clientInfo);

        try (MockedStatic<RequestContextHolder> mockedHolder = mockStatic(RequestContextHolder.class)) {
            mockedHolder
                    .when(RequestContextHolder::currentRequestAttributes)
                    .thenThrow(new IllegalStateException("No request context available"));

            // when
            var context = factory.forStatefulTransport(exchange, toolName, givenOperation());

            // then
            then(context).isNotNull();
            then(context.sessionId()).isEqualTo(sessionId);
            then(context.toolName()).isEqualTo(toolName);
        }
    }

    private FullOperation givenOperation() {
        return new FullOperation("/", PathItem.HttpMethod.GET, new Operation(), new OpenAPI());
    }
}
