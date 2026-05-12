package com.infobip.openapi.mcp;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Callback invoked by the framework to send a {@code notifications/progress} message to the MCP client.
 *
 * <p>Instances are created by {@link McpRequestContextFactory} and stored in {@link McpRequestContext}.
 * Framework users should not implement this interface directly — use
 * {@link com.infobip.openapi.mcp.progress.ProgressUpdateProvider} to customise the content of progress
 * notifications instead.
 */
@NullMarked
@FunctionalInterface
public interface ProgressNotificationCallback {

    /**
     * Sends a progress notification.
     *
     * @param progress the current progress value; must be greater than the previously sent value
     * @param total    the expected total value at completion, or {@code null} if unknown
     * @param message  optional human-readable status message, or {@code null} to omit it
     */
    void send(double progress, @Nullable Double total, @Nullable String message);
}
