package com.infobip.openapi.mcp.prompt;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NonNull;

/**
 * PromptCallFilterChain represents a chain of {@link PromptCallFilter} instances that process MCP prompt calls.
 * <p>
 * This interface is used by {@link PromptCallFilter} implementations to delegate processing
 * to the next filter in the chain. Filters should call {@link #doFilter(McpRequestContext, McpSchema.GetPromptRequest)}
 * to continue the chain execution, which will invoke the next filter based on the order defined
 * by Spring's {@link org.springframework.core.Ordered} interface.
 *
 * @see PromptCallFilter
 * @see OrderingPromptCallFilterChainFactory
 */
public interface PromptCallFilterChain {

    /**
     * Proceeds with the filter chain by invoking the next filter.
     * <p>
     * This method should be called by {@link PromptCallFilter} implementations to delegate
     * processing to the next filter in the chain. If all filters have been processed,
     * this will invoke the final handler which executes the actual prompt resolution.
     *
     * @param ctx the MCP request context containing transport metadata and HTTP request information
     * @param req the prompt call request to process
     * @return the prompt result from the chain
     * @throws IllegalStateException if the chain is exhausted without any filter returning a response
     */
    McpSchema.@NonNull GetPromptResult doFilter(
            @NonNull McpRequestContext ctx, McpSchema.@NonNull GetPromptRequest req);
}
