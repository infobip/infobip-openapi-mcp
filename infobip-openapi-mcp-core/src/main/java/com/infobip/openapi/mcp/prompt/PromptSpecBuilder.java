package com.infobip.openapi.mcp.prompt;

import com.infobip.openapi.mcp.McpRequestContextFactory;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;

/**
 * Builds MCP prompt specifications from {@link RegisteredPrompt} instances for different
 * transport types (stateful and stateless).
 */
public class PromptSpecBuilder {

    private final McpRequestContextFactory contextFactory;

    public PromptSpecBuilder(McpRequestContextFactory contextFactory) {
        this.contextFactory = contextFactory;
    }

    public McpServerFeatures.SyncPromptSpecification buildSyncPromptSpecification(RegisteredPrompt registeredPrompt) {
        return new McpServerFeatures.SyncPromptSpecification(
                registeredPrompt.prompt(),
                (exchange, request) ->
                        registeredPrompt.handler().apply(contextFactory.forPromptStatefulTransport(exchange), request));
    }

    public McpStatelessServerFeatures.SyncPromptSpecification buildSyncStatelessPromptSpecification(
            RegisteredPrompt registeredPrompt) {
        return new McpStatelessServerFeatures.SyncPromptSpecification(
                registeredPrompt.prompt(), (transportContext, request) -> registeredPrompt
                        .handler()
                        .apply(contextFactory.forPromptStatelessTransport(transportContext), request));
    }
}
