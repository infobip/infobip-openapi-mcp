/**
 * MCP prompt support backed by the {@code x-mcp-prompts} OpenAPI vendor extension.
 *
 * <p>{@link com.infobip.openapi.mcp.prompt.PromptRegistry} reads prompt definitions from the
 * extension at startup and supports two modes: <b>inline mode</b> renders
 * Mustache templates server-side from user arguments, and <b>resolved mode</b>
 * delegates to a configurable HTTP endpoint (GET with query parameters or POST with a JSON body).
 * Credentials for resolved mode are forwarded via {@link com.infobip.openapi.mcp.auth.CredentialProvider}.
 */
@NullMarked
package com.infobip.openapi.mcp.prompt;

import org.jspecify.annotations.NullMarked;
