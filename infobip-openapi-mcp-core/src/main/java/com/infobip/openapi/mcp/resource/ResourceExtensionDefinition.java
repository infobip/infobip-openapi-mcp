package com.infobip.openapi.mcp.resource;

import org.jspecify.annotations.Nullable;

/**
 * Represents a single resource definition from the {@code x-mcp-resources} OpenAPI vendor
 * extension.
 *
 * <p>Exactly one of {@code uri} (concrete resource) or {@code uriTemplate} (RFC 6570 resource
 * template) must be present, and exactly one of {@code inline} (inline mode — static content) or
 * {@code resolve} (resolved mode — backend resolution) must be present. Providing both or neither
 * of either pair is a configuration error.
 *
 * @param uri         the concrete resource URI, surfaced via {@code resources/list}
 * @param uriTemplate the RFC 6570 resource template URI, surfaced via {@code resources/templates/list}
 * @param name        the unique resource name
 * @param title       a human-readable title for the resource
 * @param description a human-readable description of the resource
 * @param mimeType    the MIME type of the resource content
 * @param inline      static content served verbatim (inline mode)
 * @param resolve     configuration for the backend endpoint that resolves this resource (resolved mode)
 */
record ResourceExtensionDefinition(
        @Nullable String uri,
        @Nullable String uriTemplate,
        String name,
        @Nullable String title,
        @Nullable String description,
        @Nullable String mimeType,
        @Nullable ResourceInlineContent inline,
        @Nullable ResourceResolveConfig resolve) {

    ResourceExtensionDefinition {
        if (uri != null && uriTemplate != null) {
            throw new IllegalArgumentException(
                    "Resource '" + name + "' must define either 'uri' or 'uriTemplate', not both");
        }
        if (uri == null && uriTemplate == null) {
            throw new IllegalArgumentException("Resource '" + name
                    + "' must define either 'uri' (concrete resource) or 'uriTemplate' (resource template)");
        }
        if (inline != null && resolve != null) {
            throw new IllegalArgumentException(
                    "Resource '" + name + "' must define either 'inline' or 'resolve', not both");
        }
        if (inline == null && resolve == null) {
            throw new IllegalArgumentException("Resource '" + name
                    + "' must define either 'inline' (static content) or 'resolve' (backend resolution)");
        }
    }

    boolean isTemplate() {
        return uriTemplate != null;
    }
}
