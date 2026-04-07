/**
 * Authorization and authentication support for the MCP framework.
 * <p>
 * This package provides extension points and configuration for controlling how credentials
 * are sourced and how incoming requests are authenticated before reaching MCP tool handlers.
 * </p>
 *
 * <h2>Credential Sourcing</h2>
 * <p>
 * The {@link com.infobip.openapi.mcp.auth.AuthorizationExtractor} interface abstracts how the
 * {@code Authorization} header value is obtained from an {@link com.infobip.openapi.mcp.McpRequestContext}.
 * The default implementation reads the header directly from the incoming HTTP request. Library
 * consumers can replace this bean to supply credentials from any source — HTTP headers, token
 * vaults, secrets managers, and so on.
 * </p>
 */
@NullMarked
package com.infobip.openapi.mcp.auth;

import org.jspecify.annotations.NullMarked;
