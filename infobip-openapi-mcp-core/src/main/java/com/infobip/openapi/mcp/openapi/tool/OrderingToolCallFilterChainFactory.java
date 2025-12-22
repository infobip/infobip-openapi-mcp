package com.infobip.openapi.mcp.openapi.tool;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * OrderingToolCallFilterChainFactory creates a {@link ToolCallFilterChain} from an ordered list of {@link ToolCallFilter}s.
 * Filters are sorted using Spring's {@link AnnotationAwareOrderComparator} to respect {@code @Order} annotations.
 */
@NullMarked
public class OrderingToolCallFilterChainFactory implements Supplier<ToolCallFilterChain> {

    private final List<ToolCallFilter> filters;

    /**
     * @param filter the only filter in the chain
     */
    public OrderingToolCallFilterChainFactory(ToolCallFilter filter) {
        this(filter, List.of());
    }

    /**
     * @param filter  at least one {@link ToolCallFilter} must be provided
     * @param filters remaining filters in the chain
     * @implNote filters are sorted in the constructor, allowing for performant {@link OrderingToolCallFilterChainFactory#get()} method calls
     */
    public OrderingToolCallFilterChainFactory(ToolCallFilter filter, Collection<? extends ToolCallFilter> filters) {
        this.filters = Stream.concat(Stream.of(filter), filters.stream())
                .sorted(new AnnotationAwareOrderComparator())
                .toList();
    }

    /**
     * @return chain that iterates over provided filters in order defined by Spring's {@link Ordered}. The resulting
     * chain throws {@link IllegalStateException} in case none of the provided filters returns a {@link McpSchema.CallToolResult}.
     */
    @Override
    public ToolCallFilterChain get() {
        return new ToolCallFilterChain() {
            int idx = 0;

            @Override
            public McpSchema.CallToolResult doFilter(McpRequestContext ctx, McpSchema.CallToolRequest req) {
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
