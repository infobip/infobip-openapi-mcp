package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.function.BiFunction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;

@NullMarked
public record RegisteredResource(
        McpSchema.@Nullable Resource resource,
        McpSchema.@Nullable ResourceTemplate resourceTemplate,
        BiFunction<McpRequestContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler)
        implements ResourceCallFilter, Ordered {

    public static final Integer ORDER = LOWEST_PRECEDENCE;

    public RegisteredResource {
        if ((resource == null) == (resourceTemplate == null)) {
            throw new IllegalArgumentException("Exactly one of resource or resourceTemplate must be provided.");
        }
    }

    public static RegisteredResource ofResource(
            McpSchema.Resource resource,
            BiFunction<McpRequestContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler) {
        return new RegisteredResource(resource, null, handler);
    }

    public static RegisteredResource ofResourceTemplate(
            McpSchema.ResourceTemplate resourceTemplate,
            BiFunction<McpRequestContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler) {
        return new RegisteredResource(null, resourceTemplate, handler);
    }

    public boolean isTemplate() {
        return resourceTemplate != null;
    }

    public String name() {
        return isTemplate() ? resourceTemplate.name() : resource.name();
    }

    /**
     * Returns the URI template of a resource template, or the URI of a static resource.
     */
    public String uri() {
        return isTemplate() ? resourceTemplate.uriTemplate() : resource.uri();
    }

    @Override
    public McpSchema.ReadResourceResult doFilter(
            McpRequestContext ctx, McpSchema.ReadResourceRequest req, ResourceCallFilterChain chain) {
        return handler.apply(ctx, req);
    }

    @Override
    public int getOrder() {
        return RegisteredResource.ORDER;
    }
}
