package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import com.infobip.openapi.mcp.McpRequestContextFactory;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Builds MCP resource and resource template specifications from {@link RegisteredResource}
 * instances for different transport types (stateful and stateless).
 */
public class ResourceSpecBuilder {

    private final List<ResourceCallFilter> filters;
    private final McpRequestContextFactory contextFactory;

    public ResourceSpecBuilder(List<ResourceCallFilter> filters, McpRequestContextFactory contextFactory) {
        this.filters = filters;
        this.contextFactory = contextFactory;
    }

    public McpServerFeatures.SyncResourceSpecification buildSyncResourceSpecification(
            RegisteredResource registeredResource) {
        return new McpServerFeatures.SyncResourceSpecification(
                registeredResource.resource(),
                buildHandler(registeredResource, contextFactory::forResourceStatefulTransport));
    }

    public McpStatelessServerFeatures.SyncResourceSpecification buildSyncStatelessResourceSpecification(
            RegisteredResource registeredResource) {
        return new McpStatelessServerFeatures.SyncResourceSpecification(
                registeredResource.resource(),
                buildHandler(registeredResource, contextFactory::forResourceStatelessTransport));
    }

    public McpServerFeatures.SyncResourceTemplateSpecification buildSyncResourceTemplateSpecification(
            RegisteredResource registeredResource) {
        return new McpServerFeatures.SyncResourceTemplateSpecification(
                registeredResource.template(),
                buildHandler(registeredResource, contextFactory::forResourceStatefulTransport));
    }

    public McpStatelessServerFeatures.SyncResourceTemplateSpecification buildSyncStatelessResourceTemplateSpecification(
            RegisteredResource registeredResource) {
        return new McpStatelessServerFeatures.SyncResourceTemplateSpecification(
                registeredResource.template(),
                buildHandler(registeredResource, contextFactory::forResourceStatelessTransport));
    }

    private <T> BiFunction<T, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> buildHandler(
            RegisteredResource registeredResource, Function<T, McpRequestContext> contextResolver) {
        var chainFactory = new OrderingResourceCallFilterChainFactory(registeredResource, filters);
        return (transport, request) -> chainFactory.get().doFilter(contextResolver.apply(transport), request);
    }
}
