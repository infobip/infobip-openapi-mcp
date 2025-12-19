package com.infobip.openapi.mcp.openapi.tool;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NonNull;

/**
 * ToolCallFilterChain represents a chain of {@link ToolCallFilter} instances that process MCP tool calls.
 * <p>
 * This interface is used by {@link ToolCallFilter} implementations to delegate processing
 * to the next filter in the chain. Filters should call {@link #doFilter(McpRequestContext, McpSchema.CallToolRequest)}
 * to continue the chain execution, which will invoke the next filter based on the order defined
 * by Spring's {@link org.springframework.core.Ordered} interface.
 *
 * @see ToolCallFilter
 * @see OrderingToolCallFilterChainFactory
 */
public interface ToolCallFilterChain {
    /**
     * Proceeds with the filter chain by invoking the next filter.
     * <p>
     * This method should be called by {@link ToolCallFilter} implementations to delegate
     * processing to the next filter in the chain. If all filters have been processed,
     * this will invoke the final handler which executes the actual tool call.
     *
     * @param ctx the MCP request context containing transport metadata and HTTP request information
     * @param req the tool call request to process
     * @return the tool call result from the chain
     * @throws IllegalStateException if the chain is exhausted without any filter returning a response
     */
    McpSchema.@NonNull CallToolResult doFilter(@NonNull McpRequestContext ctx, McpSchema.@NonNull CallToolRequest req);
}
