package com.infobip.openapi.mcp.prompt;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.function.BiFunction;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.Ordered;

/**
 * Represents a registered MCP prompt with its schema definition and handler function.
 * <p>
 * The handler receives the {@link McpRequestContext} for credential extraction and
 * the {@link McpSchema.GetPromptRequest} containing the prompt name and arguments.
 *
 * @param prompt  the MCP prompt schema definition
 * @param handler the function that resolves prompt arguments into a prompt result
 * @see McpRequestContext
 */
@NullMarked
public record RegisteredPrompt(
        McpSchema.Prompt prompt,
        BiFunction<McpRequestContext, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> handler)
        implements PromptCallFilter, Ordered {

    public static final Integer ORDER = LOWEST_PRECEDENCE;

    @Override
    public McpSchema.GetPromptResult doFilter(
            McpRequestContext ctx, McpSchema.GetPromptRequest req, PromptCallFilterChain chain) {
        return handler.apply(ctx, req);
    }

    @Override
    public int getOrder() {
        return RegisteredPrompt.ORDER;
    }
}
