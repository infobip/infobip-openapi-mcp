package com.infobip.openapi.mcp.progress;

import com.infobip.openapi.mcp.McpRequestContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Default {@link ProgressUpdateProvider} that sends an incrementing counter with no total or message.
 *
 * <p>Each call to {@link #next} returns a {@link ProgressUpdate} whose {@code progress} equals
 * the current {@code tick}, satisfying the MCP requirement that {@code progress} increases with
 * every notification. {@link #total} always returns {@code null}, indicating an unknown duration.
 *
 * <p>This bean is registered with {@code @ConditionalOnMissingBean}, so library consumers can
 * replace it by declaring their own {@link ProgressUpdateProvider} bean.
 */
@NullMarked
public class DefaultProgressUpdateProvider implements ProgressUpdateProvider {

    @Override
    public @Nullable Double total(McpRequestContext context) {
        return null;
    }

    @Override
    public ProgressUpdate next(long tick, McpRequestContext context) {
        return new ProgressUpdate(tick, null);
    }
}
