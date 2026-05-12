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
        @Nullable FullOperation openApiOperation,
        @Nullable ProgressNotificationCallback progressNotificationCallback) {
    public McpRequestContext() {
        this(null, null, null, null, null, null);
    }

    public McpRequestContext(HttpServletRequest httpServletRequest) {
        this(httpServletRequest, null, null, null, null, null);
    }

    /**
     * Returns {@code true} if the MCP client included a {@code progressToken} in the request,
     * meaning it wants to receive {@code notifications/progress} messages for this operation.
     * <p>
     * The framework uses this to decide whether to spawn a progress notification thread.
     * Callers should check this before invoking {@link #notifyOfProgress} in a loop, so that
     * no work is done when no client is listening.
     * </p>
     */
    public boolean supportsProgressNotifications() {
        return progressNotificationCallback != null;
    }

    /**
     * Sends a progress notification to the MCP client with no total and no message.
     *
     * @param progress the new progress value; must be greater than the previously sent value
     * @see #notifyOfProgress(double, Double, String)
     */
    public void notifyOfProgress(double progress) {
        notifyOfProgress(progress, null, null);
    }

    /**
     * Sends a progress notification to the MCP client with no total.
     *
     * @param progress the new progress value; must be greater than the previously sent value
     * @param message  optional human-readable status message, or {@code null} to omit it
     * @see #notifyOfProgress(double, Double, String)
     */
    public void notifyOfProgress(double progress, @Nullable String message) {
        notifyOfProgress(progress, null, message);
    }

    /**
     * Sends a progress notification to the MCP client.
     * <p>
     * Has no effect when {@link #supportsProgressNotifications()} returns {@code false}.
     * </p>
     *
     * @param progress the new progress value; must be greater than the previously sent value
     * @param total    the expected total value at completion, or {@code null} if unknown;
     *                 should remain constant across all notifications for a single tool call
     * @param message  optional human-readable status message, or {@code null} to omit it
     */
    public void notifyOfProgress(double progress, @Nullable Double total, @Nullable String message) {
        if (supportsProgressNotifications()) {
            progressNotificationCallback.send(progress, total, message);
        }
    }
}
