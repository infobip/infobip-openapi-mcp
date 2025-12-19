package com.infobip.openapi.mcp.openapi.tool;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

class OrderingToolCallFilterChainFactoryTest {

    @Order(1)
    static class MockLoggingFilter implements ToolCallFilter {
        @Override
        public McpSchema.@NonNull CallToolResult doFilter(
                @NonNull McpRequestContext ctx,
                McpSchema.@NonNull CallToolRequest req,
                @NonNull ToolCallFilterChain chain) {
            return chain.doFilter(ctx, req);
        }
    }

    static class MockRespondingFilter implements ToolCallFilter, Ordered {

        @Override
        public McpSchema.@NonNull CallToolResult doFilter(
                @NonNull McpRequestContext ctx,
                McpSchema.@NonNull CallToolRequest req,
                @NonNull ToolCallFilterChain chain) {
            return new McpSchema.CallToolResult("\"ok\"", false);
        }

        @Override
        public int getOrder() {
            return 2;
        }
    }

    @Test
    void shouldInvokeFiltersInOrder() {
        // given
        var givenCtx = new McpRequestContext();
        var givenReq = new McpSchema.CallToolRequest("tool", Map.of());
        var givenRespondingFilter = new MockRespondingFilter();
        var givenLoggingFilter = new MockLoggingFilter();

        // when
        var firstFactory = new OrderingToolCallFilterChainFactory(givenRespondingFilter, List.of(givenLoggingFilter));
        var firstRes = firstFactory.get().doFilter(givenCtx, givenReq);

        var secondFactory = new OrderingToolCallFilterChainFactory(givenLoggingFilter, List.of(givenRespondingFilter));
        var secondRes = secondFactory.get().doFilter(givenCtx, givenReq);

        // then
        then(firstRes).isNotNull().extracting(McpSchema.CallToolResult::isError).isEqualTo(false);
        then(secondRes)
                .isNotNull()
                .extracting(McpSchema.CallToolResult::isError)
                .isEqualTo(false);
    }

    @Test
    void shouldThrowIfNotFilterProvidesResult() {
        // given
        var givenCtx = new McpRequestContext();
        var givenReq = new McpSchema.CallToolRequest("tool", Map.of());
        var givenLoggingFilter = new MockLoggingFilter();

        // when
        var factory = new OrderingToolCallFilterChainFactory(givenLoggingFilter, List.of());
        var exception = thenThrownBy(() -> factory.get().doFilter(givenCtx, givenReq));

        // then
        exception
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Tool call filter chain exhausted without any of the filters returning a response.");
    }
}
