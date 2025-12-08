package com.infobip.openapi.mcp.openapi.tool;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NonNull;

public interface ToolCallFilter {

    interface Chain {
        McpSchema.@NonNull CallToolResult doFilter(
                @NonNull McpRequestContext ctx, McpSchema.@NonNull CallToolRequest req);
    }

    McpSchema.@NonNull CallToolResult doFilter(
            @NonNull McpRequestContext ctx, McpSchema.@NonNull CallToolRequest req, @NonNull Chain chain);
}
