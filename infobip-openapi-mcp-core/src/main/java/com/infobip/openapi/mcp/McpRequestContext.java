package com.infobip.openapi.mcp;

import com.infobip.openapi.mcp.enricher.ApiRequestEnricher;
import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Context object that encapsulates request-level information for MCP operations.
 * <p>
 * This record provides access to the HTTP servlet request, the MCP tool request, the MCP
 * server exchange (for stateful transports), and the OpenAPI operation that backs the tool.
 * It is created at the transport layer using {@link McpRequestContextFactory} and passed
 * explicitly through the call chain to enrichers and handlers.
 *
 * @param httpServletRequest  the HTTP servlet request, or null if not available
 * @param callToolRequest     the MCP tool invocation request, or null outside tool invocation context
 * @param asyncServerExchange the async MCP server exchange for async transports, or null
 * @param syncServerExchange  the sync MCP server exchange for sync transports, or null
 * @param openApiOperation    the set of information from OpenAPI specification that
 *                            defines the API endpoint backing this tool
 * @see ApiRequestEnricher
 * @see McpRequestContextFactory
 */
@NullMarked
public record McpRequestContext(
        @Nullable HttpServletRequest httpServletRequest,
        McpSchema.@Nullable CallToolRequest callToolRequest,
        @Nullable McpAsyncServerExchange asyncServerExchange,
        @Nullable McpSyncServerExchange syncServerExchange,
        @Nullable FullOperation openApiOperation) {
    public McpRequestContext() {
        this(null, null, null, null, null);
    }

    public McpRequestContext(HttpServletRequest httpServletRequest) {
        this(httpServletRequest, null, null, null, null);
    }

    public @Nullable String sessionId() {
        return asyncServerExchange != null
                ? asyncServerExchange.sessionId()
                : syncServerExchange != null ? syncServerExchange.sessionId() : null;
    }

    public McpSchema.@Nullable Implementation clientInfo() {
        return asyncServerExchange != null
                ? asyncServerExchange.getClientInfo()
                : syncServerExchange != null ? syncServerExchange.getClientInfo() : null;
    }

    public @Nullable String toolName() {
        return callToolRequest != null ? callToolRequest.name() : null;
    }

    public @Nullable Consumer<McpSchema.ProgressNotification> progressNotification() {
        return asyncServerExchange != null
                ? asyncServerExchange::progressNotification
                : syncServerExchange != null ? syncServerExchange::progressNotification : null;
    }
}
