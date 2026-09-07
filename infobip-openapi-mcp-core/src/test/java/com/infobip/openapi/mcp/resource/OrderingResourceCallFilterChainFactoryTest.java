package com.infobip.openapi.mcp.resource;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

class OrderingResourceCallFilterChainFactoryTest {

    @Order(1)
    static class MockLoggingFilter implements ResourceCallFilter {
        @Override
        public McpSchema.@NonNull ReadResourceResult doFilter(
                @NonNull McpRequestContext ctx,
                McpSchema.@NonNull ReadResourceRequest req,
                @NonNull ResourceCallFilterChain chain) {
            return chain.doFilter(ctx, req);
        }
    }

    static class MockRespondingFilter implements ResourceCallFilter, Ordered {

        @Override
        public McpSchema.@NonNull ReadResourceResult doFilter(
                @NonNull McpRequestContext ctx,
                McpSchema.@NonNull ReadResourceRequest req,
                @NonNull ResourceCallFilterChain chain) {
            return new McpSchema.ReadResourceResult(List.of(), null);
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
        var givenReq = new McpSchema.ReadResourceRequest("res://greet", null);
        var givenRespondingFilter = new MockRespondingFilter();
        var givenLoggingFilter = new MockLoggingFilter();

        // when
        var firstFactory =
                new OrderingResourceCallFilterChainFactory(givenRespondingFilter, List.of(givenLoggingFilter));
        var firstRes = firstFactory.get().doFilter(givenCtx, givenReq);

        var secondFactory =
                new OrderingResourceCallFilterChainFactory(givenLoggingFilter, List.of(givenRespondingFilter));
        var secondRes = secondFactory.get().doFilter(givenCtx, givenReq);

        // then
        then(firstRes)
                .isNotNull()
                .extracting(McpSchema.ReadResourceResult::contents)
                .isEqualTo(List.of());
        then(secondRes)
                .isNotNull()
                .extracting(McpSchema.ReadResourceResult::contents)
                .isEqualTo(List.of());
    }

    @Test
    void shouldThrowIfNoFilterProvidesResult() {
        // given
        var givenCtx = new McpRequestContext();
        var givenReq = new McpSchema.ReadResourceRequest("res://greet", null);
        var givenLoggingFilter = new MockLoggingFilter();

        // when
        var factory = new OrderingResourceCallFilterChainFactory(givenLoggingFilter, List.of());
        var exception = thenThrownBy(() -> factory.get().doFilter(givenCtx, givenReq));

        // then
        exception
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Resource call filter chain exhausted without any of the filters returning a response.");
    }
}
