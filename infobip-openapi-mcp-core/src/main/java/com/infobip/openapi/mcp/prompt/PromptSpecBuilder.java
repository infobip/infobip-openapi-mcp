package com.infobip.openapi.mcp.prompt;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.McpRequestContextFactory;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

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
        return new McpServerFeatures.SyncPromptSpecification(
                registeredPrompt.prompt(), buildHandler(registeredPrompt, contextFactory::forPromptStatefulTransport));
    }

    public McpStatelessServerFeatures.SyncPromptSpecification buildSyncStatelessPromptSpecification(
            RegisteredPrompt registeredPrompt) {
        return new McpStatelessServerFeatures.SyncPromptSpecification(
                registeredPrompt.prompt(), buildHandler(registeredPrompt, contextFactory::forPromptStatelessTransport));
    }

    private <T> BiFunction<T, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> buildHandler(
            RegisteredPrompt registeredPrompt, Function<T, McpRequestContext> contextResolver) {
        var chainFactory = new OrderingPromptCallFilterChainFactory(registeredPrompt, filters);
        return (transport, request) -> chainFactory.get().doFilter(contextResolver.apply(transport), request);
    }
}
