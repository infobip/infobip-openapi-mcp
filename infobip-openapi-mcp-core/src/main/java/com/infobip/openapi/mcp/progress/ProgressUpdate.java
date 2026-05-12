package com.infobip.openapi.mcp.progress;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The content of a single {@code notifications/progress} message produced by a
 * {@link ProgressUpdateProvider}.
 *
 * <p>{@code progress} must increase with each successive notification. {@code message} is optional
 * human-readable text shown to the user in supporting MCP clients.
 *
 * <p>{@code total} is resolved once per tool call by
 * {@link ProgressUpdateProvider#total(com.infobip.openapi.mcp.McpRequestContext)} and remains
 * constant throughout — it is not part of this record to prevent it from drifting between
 * notifications, which would confuse clients that display a progress fraction.
 *
 * @param progress the current progress value; must be greater than the value sent in the previous
 *                 notification for this tool call
 * @param message  optional human-readable status message, or {@code null} to omit it
 */
@NullMarked
public record ProgressUpdate(double progress, @Nullable String message) {}
