/**
 * MCP prompt support backed by the {@code x-mcp-prompts} OpenAPI vendor extension.
 *
 * <p>{@link com.infobip.openapi.mcp.prompt.PromptRegistry} reads prompt definitions from the
 * extension at startup and resolves them at runtime by calling a configurable backend endpoint
 * (GET with query parameters or POST with a JSON body). Credentials are forwarded via
 * {@link com.infobip.openapi.mcp.auth.CredentialProvider}.
 */
@NullMarked
package com.infobip.openapi.mcp.prompt;

import org.jspecify.annotations.NullMarked;
