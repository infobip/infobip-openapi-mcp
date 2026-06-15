package com.infobip.openapi.mcp.prompt;

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
 * OrderingPromptCallFilterChainFactory creates a {@link PromptCallFilterChain} from an ordered list of {@link PromptCallFilter}s.
 * Filters are sorted using Spring's {@link AnnotationAwareOrderComparator} to respect {@code @Order} annotations.
 */
@NullMarked
public class OrderingPromptCallFilterChainFactory implements Supplier<PromptCallFilterChain> {

    private final List<PromptCallFilter> filters;

    /**
     * @param filter the only filter in the chain
     */
    public OrderingPromptCallFilterChainFactory(PromptCallFilter filter) {
        this(filter, List.of());
    }

    /**
     * @param filter  at least one {@link PromptCallFilter} must be provided
     * @param filters remaining filters in the chain
     * @implNote filters are sorted in the constructor, allowing for performant {@link OrderingPromptCallFilterChainFactory#get()} method calls
     */
    public OrderingPromptCallFilterChainFactory(
            PromptCallFilter filter, Collection<? extends PromptCallFilter> filters) {
        this.filters = Stream.concat(Stream.of(filter), filters.stream())
                .sorted(new AnnotationAwareOrderComparator())
                .toList();
    }

    /**
     * @return chain that iterates over provided filters in order defined by Spring's {@link Ordered}. The resulting
     * chain throws {@link IllegalStateException} in case none of the provided filters returns a {@link McpSchema.GetPromptResult}.
     */
    @Override
    public PromptCallFilterChain get() {
        return new PromptCallFilterChain() {
            int idx = 0;

            @Override
            public McpSchema.GetPromptResult doFilter(McpRequestContext ctx, McpSchema.GetPromptRequest req) {
                if (idx >= filters.size()) {
                    throw new IllegalStateException(
                            "Prompt call filter chain exhausted without any of the filters returning a response.");
                }
                var nextFilter = filters.get(idx++);
                return nextFilter.doFilter(ctx, req, this);
            }
        };
    }
}
