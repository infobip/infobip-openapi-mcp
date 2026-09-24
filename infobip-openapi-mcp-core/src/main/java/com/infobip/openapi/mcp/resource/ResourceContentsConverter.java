package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NullMarked;

/**
 * Converts the downstream API response of a resource read into the MCP resource contents returned to the client.
 *
 * <p>MCP resource contents are either textual ({@link McpSchema.TextResourceContents}) or binary, base64 encoded
 * ({@link McpSchema.BlobResourceContents}). The converter decides which of the two to return and which
 * {@code mimeType} to report. The default bean ({@link DefaultResourceContentsConverter}) decides based on the media
 * type of the response; library consumers can replace it to support custom media types, apply size limits, or
 * customize the conversion per resource.
 *
 * <p><strong>Example — treat a vendor media type as text, delegate everything else to the default:</strong>
 * <pre>{@code
 * @Bean
 * public ResourceContentsConverter resourceContentsConverter() {
 *     var defaultConverter = new DefaultResourceContentsConverter();
 *     var vendorType = MediaType.parseMediaType("application/vnd.acme.config");
 *     return (response, context) -> {
 *         var contentType = response.headers().getContentType();
 *         if (contentType != null && vendorType.includes(contentType)) {
 *             return new McpSchema.TextResourceContents(
 *                     response.uri(), vendorType.toString(), new String(response.body(), StandardCharsets.UTF_8));
 *         }
 *         return defaultConverter.convert(response, context);
 *     };
 * }
 * }</pre>
 */
@NullMarked
@FunctionalInterface
public interface ResourceContentsConverter {

    /**
     * Converts a successful downstream API response into MCP resource contents.
     *
     * @param response the downstream API response along with the resource it was read for
     * @param context  the MCP request context of the resource read
     * @return the resource contents returned to the MCP client
     */
    McpSchema.ResourceContents convert(ResourceResponse response, McpRequestContext context);
}
