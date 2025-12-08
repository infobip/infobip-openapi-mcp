package com.infobip.openapi.mcp;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Factory for creating {@link McpRequestContext} instances
 * <p>
 * This factory encapsulates the logic for extracting HTTP servlet requests from Spring's
 * {@link RequestContextHolder} and combining them with MCP transport metadata to create
 * context objects. By centralizing this logic in a factory, we enable easier testing and
 * clearer separation of concerns.
 * </p>
 *
 * <h3>Benefits:</h3>
 * <ul>
 *   <li>Easier mocking in tests - tests can create {@link McpRequestContext} records directly</li>
 *   <li>Encapsulates Spring-specific {@link RequestContextHolder} access</li>
 *   <li>Provides consistent context creation across stateful and stateless transports</li>
 *   <li>Extracts HTTP request once at the transport layer for reliability</li>
 * </ul>
 *
 * @see McpRequestContext
 */
@NullMarked
public class McpRequestContextFactory {

    /**
     * Creates an MCP request context for stateful transport protocols (SSE, Streamable, Stdio).
     * <p>
     * Stateful transports maintain a persistent connection/session with the client, so this
     * factory method extracts session ID and client information from the exchange using reflection.
     * The exchange parameter is typed as Object to avoid compile-time dependencies on specific
     * MCP transport types.
     * </p>
     *
     * @param exchange      the MCP server exchange containing session information
     * @param toolName      the name of the MCP tool being invoked, or null if not in tool invocation context
     * @param fullOperation the set of information from OpenAPI specification that
     *                      defines the API endpoint backing this tool
     * @return a new context instance with stateful transport metadata and tool name
     */
    public McpRequestContext forStatefulTransport(
            McpSyncServerExchange exchange, @Nullable String toolName, FullOperation fullOperation) {
        var httpServletRequest = getCurrentHttpServletRequest();
        var sessionId = exchange.sessionId();
        var clientInfo = exchange.getClientInfo();
        return new McpRequestContext(httpServletRequest, sessionId, clientInfo, toolName, fullOperation);
    }

    /**
     * Creates an MCP request context for stateless transport protocol (HTTP).
     * <p>
     * Stateless transports don't maintain persistent sessions, so session ID and client
     * information are not available.
     * </p>
     *
     * @param transportContext the MCP transport context (may be null, currently unused)
     * @param toolName         the name of the MCP tool being invoked, or null if not in tool invocation context
     * @param fullOperation    the set of information from OpenAPI specification that
     *                         defines the API endpoint backing this tool
     * @return a new context instance without session metadata but with tool name
     */
    public McpRequestContext forStatelessTransport(
            @Nullable McpTransportContext transportContext, @Nullable String toolName, FullOperation fullOperation) {
        var httpServletRequest = getCurrentHttpServletRequest();
        // Stateless transport doesn't have session or client info (yet)
        return new McpRequestContext(httpServletRequest, null, null, toolName, fullOperation);
    }

    /**
     * Creates an MCP request context for servlet filters.
     * <p>
     * Servlet filters have direct access to the {@link HttpServletRequest} and don't need
     * to extract it from {@link RequestContextHolder}. This method is specifically designed
     * for filters that intercept requests before they reach the MCP transport layer.
     * </p>
     *
     * @param request the HTTP servlet request from the filter
     * @return a new context instance with the provided request and no session metadata
     */
    public McpRequestContext forServletFilter(HttpServletRequest request) {
        return new McpRequestContext(request);
    }

    /**
     * Extracts the current HTTP servlet request from Spring's {@link RequestContextHolder}.
     * <p>
     * This method attempts to retrieve the HTTP request associated with the current thread.
     * It may return null in non-web contexts (e.g., stdio transport) or when the HTTP request
     * is not accessible.
     * </p>
     *
     * @return the HTTP servlet request, or null if not available
     */
    private @Nullable HttpServletRequest getCurrentHttpServletRequest() {
        try {
            var requestAttributes = RequestContextHolder.currentRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
                return servletRequestAttributes.getRequest();
            }
        } catch (IllegalStateException e) {
            // No request context available (e.g., stdio transport or non-web context)
            return null;
        }
        return null;
    }
}
