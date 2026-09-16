package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

@NullMarked
public class OrderingResourceCallFilterChainFactory implements Supplier<ResourceCallFilterChain> {

    private final List<ResourceCallFilter> filters;

    public OrderingResourceCallFilterChainFactory(ResourceCallFilter filter) {
        this(filter, List.of());
    }

    public OrderingResourceCallFilterChainFactory(
            ResourceCallFilter filter, Collection<? extends ResourceCallFilter> filters) {
        this.filters = Stream.concat(Stream.of(filter), filters.stream())
                .sorted(new AnnotationAwareOrderComparator())
                .toList();
    }

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
