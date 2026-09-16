package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NonNull;

public interface ResourceCallFilterChain {

    McpSchema.@NonNull ReadResourceResult doFilter(
            @NonNull McpRequestContext ctx, McpSchema.@NonNull ReadResourceRequest req);
}
