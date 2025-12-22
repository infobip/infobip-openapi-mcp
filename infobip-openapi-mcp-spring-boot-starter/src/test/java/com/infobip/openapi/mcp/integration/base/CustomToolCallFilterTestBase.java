package com.infobip.openapi.mcp.integration.base;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.openapi.tool.ToolCallFilter;
import com.infobip.openapi.mcp.openapi.tool.ToolCallFilterChain;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {CustomToolCallFilterTestBase.TestConfig.class})
public abstract class CustomToolCallFilterTestBase extends IntegrationTestBase {

    record TestToolCallFilter(
            AtomicReference<@Nullable McpRequestContext> observedCtx,
            AtomicReference<McpSchema.@Nullable CallToolRequest> observedReq,
            AtomicReference<McpSchema.@Nullable CallToolResult> observedRes)
            implements ToolCallFilter, Ordered {
        public TestToolCallFilter() {
            this(new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
        }

        @Override
        public McpSchema.@Nullable CallToolResult doFilter(
                @NonNull McpRequestContext ctx,
                McpSchema.@NonNull CallToolRequest req,
                @NonNull ToolCallFilterChain chain) {
            observedCtx.set(ctx);
            observedReq.set(req);
            var res = chain.doFilter(ctx, req);
            observedRes.set(res);
            return res;
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestToolCallFilter testToolCallFilter() {
            return new TestToolCallFilter();
        }
    }

    @Autowired
    private TestToolCallFilter testToolCallFilter;

    @Test
    void shouldInvokeCustomToolCallFilter() {
        withInitializedMcpClient(givenClient -> {
            // When
            givenClient.callTool(McpSchema.CallToolRequest.builder()
                    .name("get_users")
                    .arguments(Map.of())
                    .build());

            // Then
            then(testToolCallFilter.observedCtx().get()).isNotNull();
            then(testToolCallFilter.observedReq().get()).isNotNull();
            then(testToolCallFilter.observedRes().get()).isNotNull();
        });
    }
}
