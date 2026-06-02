package com.infobip.openapi.mcp.prompt;

import com.infobip.openapi.mcp.McpRequestContextFactory;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import java.util.List;

/**
 * Builds MCP prompt specifications from {@link RegisteredPrompt} instances for different
 * transport types (stateful and stateless).
 */
public class PromptSpecBuilder {

    private final List<PromptCallFilter> filters;
    private final McpRequestContextFactory contextFactory;

    public PromptSpecBuilder(List<PromptCallFilter> filters, McpRequestContextFactory contextFactory) {
        this.filters = filters;
        this.contextFactory = contextFactory;
    }

    public McpServerFeatures.SyncPromptSpecification buildSyncPromptSpecification(RegisteredPrompt registeredPrompt) {
        var chainFactory = new OrderingPromptCallFilterChainFactory(registeredPrompt, filters);
        return new McpServerFeatures.SyncPromptSpecification(registeredPrompt.prompt(), (exchange, request) -> {
            var context = contextFactory.forPromptStatefulTransport(exchange);
            return chainFactory.get().doFilter(context, request);
        });
    }

    public McpStatelessServerFeatures.SyncPromptSpecification buildSyncStatelessPromptSpecification(
            RegisteredPrompt registeredPrompt) {
        var chainFactory = new OrderingPromptCallFilterChainFactory(registeredPrompt, filters);
        return new McpStatelessServerFeatures.SyncPromptSpecification(
                registeredPrompt.prompt(), (transportContext, request) -> {
                    var context = contextFactory.forPromptStatelessTransport(transportContext);
                    return chainFactory.get().doFilter(context, request);
                });
    }
}
