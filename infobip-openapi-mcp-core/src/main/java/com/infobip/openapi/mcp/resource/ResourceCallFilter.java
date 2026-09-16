package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ResourceCallFilter {

    McpSchema.ReadResourceResult doFilter(
            McpRequestContext ctx, McpSchema.ReadResourceRequest req, ResourceCallFilterChain chain);
}
