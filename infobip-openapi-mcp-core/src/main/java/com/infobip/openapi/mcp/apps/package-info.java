/**
 * MCP Apps support backed by the {@code x-mcp-app-resource} and {@code x-mcp-app} OpenAPI vendor
 * extensions.
 *
 * <p>A {@code GET} operation returning an HTML document can be marked with
 * {@code x-mcp-app-resource} to expose it as an MCP Apps UI resource ({@code ui://} URI,
 * {@code text/html;profile=mcp-app} MIME type). Any operation that becomes a tool can be linked to
 * such a resource with {@code x-mcp-app}, so MCP Apps-capable hosts render the UI while all other
 * hosts keep working with the plain text tool result.
 *
 * <p>The vendor extension data is modelled by {@link com.infobip.openapi.mcp.apps.AppResourceExtension} and
 * {@link com.infobip.openapi.mcp.apps.AppToolExtension}, and converted to MCP {@code _meta} maps by
 * {@link com.infobip.openapi.mcp.apps.AppMeta}.
 *
 * @see <a href="https://modelcontextprotocol.io/extensions/apps/overview">MCP Apps extension</a>
 */
@NullMarked
package com.infobip.openapi.mcp.apps;

import org.jspecify.annotations.NullMarked;
