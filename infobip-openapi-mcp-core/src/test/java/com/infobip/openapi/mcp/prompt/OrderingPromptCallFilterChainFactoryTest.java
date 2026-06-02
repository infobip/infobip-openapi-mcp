package com.infobip.openapi.mcp.prompt;

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

class OrderingPromptCallFilterChainFactoryTest {

    @Order(1)
    static class MockLoggingFilter implements PromptCallFilter {
        @Override
        public McpSchema.@NonNull GetPromptResult doFilter(
                @NonNull McpRequestContext ctx,
                McpSchema.@NonNull GetPromptRequest req,
                @NonNull PromptCallFilterChain chain) {
            return chain.doFilter(ctx, req);
        }
    }

    static class MockRespondingFilter implements PromptCallFilter, Ordered {

        @Override
        public McpSchema.@NonNull GetPromptResult doFilter(
                @NonNull McpRequestContext ctx,
                McpSchema.@NonNull GetPromptRequest req,
                @NonNull PromptCallFilterChain chain) {
            return new McpSchema.GetPromptResult("test", List.of());
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
        var givenReq = new McpSchema.GetPromptRequest("prompt", Map.of());
        var givenRespondingFilter = new MockRespondingFilter();
        var givenLoggingFilter = new MockLoggingFilter();

        // when
        var firstFactory = new OrderingPromptCallFilterChainFactory(givenRespondingFilter, List.of(givenLoggingFilter));
        var firstRes = firstFactory.get().doFilter(givenCtx, givenReq);

        var secondFactory =
                new OrderingPromptCallFilterChainFactory(givenLoggingFilter, List.of(givenRespondingFilter));
        var secondRes = secondFactory.get().doFilter(givenCtx, givenReq);

        // then
        then(firstRes)
                .isNotNull()
                .extracting(McpSchema.GetPromptResult::description)
                .isEqualTo("test");
        then(secondRes)
                .isNotNull()
                .extracting(McpSchema.GetPromptResult::description)
                .isEqualTo("test");
    }

    @Test
    void shouldThrowIfNoFilterProvidesResult() {
        // given
        var givenCtx = new McpRequestContext();
        var givenReq = new McpSchema.GetPromptRequest("prompt", Map.of());
        var givenLoggingFilter = new MockLoggingFilter();

        // when
        var factory = new OrderingPromptCallFilterChainFactory(givenLoggingFilter, List.of());
        var exception = thenThrownBy(() -> factory.get().doFilter(givenCtx, givenReq));

        // then
        exception
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Prompt call filter chain exhausted without any of the filters returning a response.");
    }
}
