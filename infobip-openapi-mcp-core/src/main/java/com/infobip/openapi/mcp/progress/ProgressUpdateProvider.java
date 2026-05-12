package com.infobip.openapi.mcp.progress;

import com.infobip.openapi.mcp.McpRequestContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Produces the content of {@code notifications/progress} messages sent to MCP clients while
 * an HTTP API call is in progress.
 *
 * <p>The framework calls {@link #total} once before the notification loop begins, then calls
 * {@link #next} on every tick. The resolved {@code total} is held constant for the lifetime of
 * the tool call so that clients always receive a consistent fraction.
 *
 * <p>Library consumers can replace the default bean
 * ({@link DefaultProgressUpdateProvider}) to supply tool-specific progress information.
 * The {@link McpRequestContext} passed to both methods carries the tool name and OpenAPI operation,
 * enabling per-operation customisation without separate beans per tool.
 *
 * <p><strong>Example — static per-operation message:</strong>
 * <pre>{@code
 * @Bean
 * public ProgressUpdateProvider progressUpdateProvider() {
 *     return new ProgressUpdateProvider() {
 *         @Override
 *         public Double total(McpRequestContext context) {
 *             return null; // unknown duration
 *         }
 *
 *         @Override
 *         public ProgressUpdate next(long tick, McpRequestContext context) {
 *             var toolName = context.toolName() != null ? context.toolName() : "tool";
 *             return new ProgressUpdate(tick, "Waiting for " + toolName + " to complete…");
 *         }
 *     };
 * }
 * }</pre>
 */
@NullMarked
public interface ProgressUpdateProvider {

    /**
     * Returns the expected total value at completion for the given tool call, or {@code null} if
     * the duration is unknown.
     *
     * <p>Called once before the notification loop starts. The returned value is sent unchanged in
     * every {@code notifications/progress} message for this tool call.
     *
     * @param context the current MCP request context
     * @return the total, or {@code null} to omit it from notifications
     */
    @Nullable
    Double total(McpRequestContext context);

    /**
     * Produces the next progress update for the given tick.
     *
     * <p>The {@code progress} value in the returned {@link ProgressUpdate} must be greater than
     * the value returned on the previous tick.
     *
     * @param tick    zero-based counter incremented by the framework on each notification interval
     * @param context the current MCP request context
     * @return the progress update to send
     */
    ProgressUpdate next(long tick, McpRequestContext context);
}
