package com.infobip.openapi.mcp.openapi.schema;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A single composed example ready for rendering in a tool description.
 * <p>
 * {@code title} and {@code description} originate from the {@code summary} and
 * {@code description} fields of an OpenAPI {@code Example} object.  Both may be
 * {@code null} when the example comes from an inline {@code example} or
 * {@code schema.example} field rather than from a named {@code examples} map.
 */
@NullMarked
public record ComposedExample(
        @Nullable String title, @Nullable String description, Object value) {}
