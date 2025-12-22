package com.infobip.openapi.mcp.openapi.tool;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.function.BiFunction;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.Ordered;

/**
 * Represents a registered tool with its corresponding MCP tool schema and handler function.
 * <p>
 * The tool handler is a {@link BiFunction} that accepts both the tool call request and
 * the MCP request context, allowing enrichers to access HTTP request data and MCP transport
 * metadata during tool execution.
 * </p>
 *
 * @param tool          The MCP tool schema definition.
 * @param toolHandler   The function that handles the tool's execution logic, accepting the call
 *                      tool request and MCP request context.
 * @param fullOperation the set of information from OpenAPI specification that
 *                      defines the API endpoint backing this tool
 * @see McpRequestContext
 */
@NullMarked
public record RegisteredTool(
        McpSchema.Tool tool,
        BiFunction<McpSchema.CallToolRequest, McpRequestContext, McpSchema.CallToolResult> toolHandler,
        FullOperation fullOperation)
        implements ToolCallFilter, Ordered {

    public static final Integer ORDER = LOWEST_PRECEDENCE;

    @Override
    public McpSchema.CallToolResult doFilter(
            McpRequestContext ctx, McpSchema.CallToolRequest req, ToolCallFilterChain chain) {
        return toolHandler.apply(req, ctx);
    }

    @Override
    public int getOrder() {
        return RegisteredTool.ORDER;
    }
}
