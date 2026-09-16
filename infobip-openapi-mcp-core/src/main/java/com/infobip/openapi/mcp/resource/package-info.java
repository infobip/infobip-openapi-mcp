/**
 * MCP resource support backed by the {@code x-mcp-resource} OpenAPI vendor extension.
 *
 * <p>{@link com.infobip.openapi.mcp.resource.ResourceRegistry} scans OpenAPI {@code GET} operations marked with the
 * extension and converts each into either a static MCP {@code Resource} (no parameters) or a {@code
 * ResourceTemplate} (path and/or query parameters present). The resulting {@code uri}/{@code uriTemplate}, {@code
 * name}, {@code title}, {@code description} and {@code mimeType} are all derived from the OpenAPI operation.
 */
@NullMarked
package com.infobip.openapi.mcp.resource;

import org.jspecify.annotations.NullMarked;
