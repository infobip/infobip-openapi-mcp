package com.infobip.openapi.mcp.resource;

import org.jspecify.annotations.Nullable;

/**
 * Static content for an inline-mode resource from the {@code x-mcp-resources} OpenAPI vendor
 * extension.
 *
 * <p>Exactly one of {@code text} (UTF-8 text content) or {@code blob} (base64-encoded binary
 * content) must be present.
 *
 * @param text UTF-8 text content, mapped to {@link io.modelcontextprotocol.spec.McpSchema.TextResourceContents}
 * @param blob base64-encoded binary content, mapped to {@link io.modelcontextprotocol.spec.McpSchema.BlobResourceContents}
 */
record ResourceInlineContent(
        @Nullable String text, @Nullable String blob) {

    ResourceInlineContent {
        if (text != null && blob != null) {
            throw new IllegalArgumentException("inline content must define either 'text' or 'blob', not both");
        }
        if (text == null && blob == null) {
            throw new IllegalArgumentException("inline content must define either 'text' or 'blob'");
        }
    }
}
