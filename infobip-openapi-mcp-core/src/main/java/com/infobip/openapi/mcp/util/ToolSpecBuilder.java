package com.infobip.openapi.mcp.util;

import com.infobip.openapi.mcp.McpRequestContextFactory;
import com.infobip.openapi.mcp.openapi.tool.OrderingToolCallFilterChainFactory;
import com.infobip.openapi.mcp.openapi.tool.RegisteredTool;
import com.infobip.openapi.mcp.openapi.tool.ToolCallFilter;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import java.util.List;

public class ToolSpecBuilder {

    private final List<ToolCallFilter> filters;
    private final McpRequestContextFactory contextFactory;

    public ToolSpecBuilder(List<ToolCallFilter> filters, McpRequestContextFactory contextFactory) {
        this.filters = filters;
        this.contextFactory = contextFactory;
    }

    public McpServerFeatures.SyncToolSpecification buildSyncToolSpecification(RegisteredTool registeredTool) {
        var chainFactory = new OrderingToolCallFilterChainFactory(registeredTool, filters);
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(registeredTool.tool())
                .callHandler((mcpSyncServerExchange, callToolRequest) -> {
                    var context = contextFactory.forStatefulTransport(
                            mcpSyncServerExchange, callToolRequest.name(), registeredTool.fullOperation());
                    return chainFactory.get().doFilter(context, callToolRequest);
                })
                .build();
    }

    public McpStatelessServerFeatures.SyncToolSpecification buildSyncStatelessToolSpecification(
            RegisteredTool registeredTool) {
        var chainFactory = new OrderingToolCallFilterChainFactory(registeredTool, filters);
        return McpStatelessServerFeatures.SyncToolSpecification.builder()
                .tool(registeredTool.tool())
                .callHandler((mcpTransportContext, callToolRequest) -> {
                    var context = contextFactory.forStatelessTransport(
                            mcpTransportContext, callToolRequest.name(), registeredTool.fullOperation());
                    return chainFactory.get().doFilter(context, callToolRequest);
                })
                .build();
    }
}
