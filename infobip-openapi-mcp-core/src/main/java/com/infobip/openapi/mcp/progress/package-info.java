/**
 * Progress notification support for long-running MCP tool calls.
 *
 * <p>The central extension point is {@link com.infobip.openapi.mcp.progress.ProgressUpdateProvider},
 * a replaceable Spring bean that controls what {@code progress}, {@code total}, and {@code message}
 * values are sent to MCP clients during HTTP API calls. The default implementation sends an
 * incrementing counter with no total or message.
 */
@NullMarked
package com.infobip.openapi.mcp.progress;

import org.jspecify.annotations.NullMarked;
