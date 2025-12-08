package com.infobip.openapi.mcp;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Context object that encapsulates request-level information for MCP operations.
 * <p>
 * This record provides access to both HTTP servlet request context (headers, IP addresses)
 * and MCP transport-specific metadata (session IDs, client information). The context is
 * created at the transport layer using {@link McpRequestContextFactory} and passed explicitly
 * through the call chain to enrichers and handlers.
 * </p>
 *
 * @param httpServletRequest the HTTP servlet request, or null if not available
 * @param sessionId          the MCP session ID for stateful transports, or null
 * @param clientInfo         the MCP client implementation information, or null
 * @param toolName           the MCP tool name being invoked, or null if not in tool invocation context
 * @param openApiOperation   the set of information from OpenAPI specification that
 *                           defines the API endpoint backing this tool
 * @see com.infobip.openapi.mcp.enricher.ApiRequestEnricher
 * @see McpRequestContextFactory
 */
@NullMarked
public record McpRequestContext(
        @Nullable HttpServletRequest httpServletRequest,
        @Nullable String sessionId,
        McpSchema.@Nullable Implementation clientInfo,
        @Nullable String toolName,
        @Nullable FullOperation openApiOperation) {
    public McpRequestContext() {
        this(null, null, null, null, null);
    }

    public McpRequestContext(HttpServletRequest httpServletRequest) {
        this(httpServletRequest, null, null, null, null);
    }
}
