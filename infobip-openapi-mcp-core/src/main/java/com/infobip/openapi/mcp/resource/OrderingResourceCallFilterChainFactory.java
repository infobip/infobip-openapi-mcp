package com.infobip.openapi.mcp.resource;

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
 * OrderingResourceCallFilterChainFactory creates a {@link ResourceCallFilterChain} from an ordered list of {@link ResourceCallFilter}s.
 * Filters are sorted using Spring's {@link AnnotationAwareOrderComparator} to respect {@code @Order} annotations.
 */
@NullMarked
public class OrderingResourceCallFilterChainFactory implements Supplier<ResourceCallFilterChain> {

    private final List<ResourceCallFilter> filters;

    /**
     * @param filter the only filter in the chain
     */
    public OrderingResourceCallFilterChainFactory(ResourceCallFilter filter) {
        this(filter, List.of());
    }

    /**
     * @param filter  at least one {@link ResourceCallFilter} must be provided
     * @param filters remaining filters in the chain
     * @implNote filters are sorted in the constructor, allowing for performant {@link OrderingResourceCallFilterChainFactory#get()} method calls
     */
    public OrderingResourceCallFilterChainFactory(
            ResourceCallFilter filter, Collection<? extends ResourceCallFilter> filters) {
        this.filters = Stream.concat(Stream.of(filter), filters.stream())
                .sorted(new AnnotationAwareOrderComparator())
                .toList();
    }

    /**
     * @return chain that iterates over provided filters in order defined by Spring's {@link Ordered}. The resulting
     * chain throws {@link IllegalStateException} in case none of the provided filters returns a {@link McpSchema.ReadResourceResult}.
     */
    @Override
    public ResourceCallFilterChain get() {
        return new ResourceCallFilterChain() {
            int idx = 0;

            @Override
            public McpSchema.ReadResourceResult doFilter(McpRequestContext ctx, McpSchema.ReadResourceRequest req) {
                if (idx >= filters.size()) {
                    throw new IllegalStateException(
                            "Resource call filter chain exhausted without any of the filters returning a response.");
                }
                var nextFilter = filters.get(idx++);
                return nextFilter.doFilter(ctx, req, this);
            }
        };
    }
}
