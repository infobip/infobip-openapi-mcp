package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.function.BiFunction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;

/**
 * Represents a registered MCP resource with its schema definition and handler function.
 * <p>
 * Exactly one of {@code resource} (concrete resource, surfaced via {@code resources/list}) or
 * {@code template} (RFC 6570 resource template, surfaced via {@code resources/templates/list})
 * is set, matching the source {@code x-mcp-resources} definition.
 * <p>
 * The handler receives the {@link McpRequestContext} for credential extraction and
 * the {@link McpSchema.ReadResourceRequest} containing the requested URI.
 *
 * @param resource the concrete MCP resource schema definition, or {@code null} if this is a template
 * @param template the MCP resource template schema definition, or {@code null} if this is a concrete resource
 * @param handler  the function that resolves a resource read request into its content
 * @see McpRequestContext
 */
@NullMarked
public record RegisteredResource(
        McpSchema.@Nullable Resource resource,
        McpSchema.@Nullable ResourceTemplate template,
        BiFunction<McpRequestContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> handler)
        implements ResourceCallFilter, Ordered {

    public static final Integer ORDER = LOWEST_PRECEDENCE;

    public RegisteredResource {
        if (resource != null && template != null) {
            throw new IllegalArgumentException("A registered resource must not have both a resource and a template");
        }
        if (resource == null && template == null) {
            throw new IllegalArgumentException("A registered resource must have either a resource or a template");
        }
    }

    public boolean isTemplate() {
        return template != null;
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
