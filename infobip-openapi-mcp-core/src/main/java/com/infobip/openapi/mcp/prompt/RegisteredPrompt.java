package com.infobip.openapi.mcp.prompt;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.function.BiFunction;

/**
 * Represents a registered MCP prompt with its schema definition and handler function.
 * <p>
 * The handler receives the {@link McpRequestContext} for credential extraction and
 * the {@link McpSchema.GetPromptRequest} containing the prompt name and arguments.
 *
 * @param prompt  the MCP prompt schema definition
 * @param handler the function that resolves prompt arguments into a prompt result
 */
public record RegisteredPrompt(
        McpSchema.Prompt prompt,
        BiFunction<McpRequestContext, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> handler) {}
