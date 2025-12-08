package com.infobip.openapi.mcp.openapi.tool;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

public class OrderingToolCallFilterChainFactory implements Supplier<ToolCallFilter.Chain> {

    private final List<ToolCallFilter> filters;

    public OrderingToolCallFilterChainFactory(
            @NonNull ToolCallFilter filter, @NonNull Collection<? extends ToolCallFilter> filters) {
        this.filters = Stream.concat(Stream.of(filter), filters.stream())
                .sorted(new AnnotationAwareOrderComparator())
                .toList();
    }

    @Override
    public ToolCallFilter.@NonNull Chain get() {
        return new ToolCallFilter.Chain() {
            int idx = 0;

            @Override
            public McpSchema.@NonNull CallToolResult doFilter(
                    @NonNull McpRequestContext ctx, McpSchema.@NonNull CallToolRequest req) {
                if (idx >= filters.size()) {
                    throw new IllegalStateException(
                            "Tool call filter chain exhausted without any of the filters returning a response.");
                }
                var nextFilter = filters.get(idx++);
                return nextFilter.doFilter(ctx, req, this);
            }
        };
    }
}
