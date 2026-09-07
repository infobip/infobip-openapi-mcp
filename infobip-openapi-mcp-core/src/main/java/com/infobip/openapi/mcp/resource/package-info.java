/**
 * MCP resource support backed by the {@code x-mcp-resources} OpenAPI vendor extension.
 *
 * <p>{@link com.infobip.openapi.mcp.resource.ResourceRegistry} reads resource definitions from the
 * extension at startup and supports two modes: <b>inline mode</b> serves static text or base64
 * blob content baked into the spec, and <b>resolved mode</b> delegates to a configurable HTTP GET
 * endpoint, passing the backend response body through verbatim as the resource content.
 * Credentials for resolved mode are forwarded via {@link com.infobip.openapi.mcp.auth.CredentialProvider}.
 *
 * <p>A definition with a {@code uri} registers a concrete {@link io.modelcontextprotocol.spec.McpSchema.Resource};
 * a definition with a {@code uriTemplate} registers an RFC 6570 {@link io.modelcontextprotocol.spec.McpSchema.ResourceTemplate}
 * whose variables are extracted from the requested URI and forwarded as query parameters in resolved mode.
 */
@NullMarked
package com.infobip.openapi.mcp.resource;

import org.jspecify.annotations.NullMarked;
