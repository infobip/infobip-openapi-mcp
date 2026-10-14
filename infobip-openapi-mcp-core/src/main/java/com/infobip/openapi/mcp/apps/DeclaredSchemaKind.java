package com.infobip.openapi.mcp.apps;

/**
 * The shape of the JSON body an operation's successful response declares, decided once at tool
 * registration time from the response schema in the OpenAPI specification.
 *
 * <p>MCP Apps UIs receive tool results as a JSON object, so a declared non-object body has to be
 * wrapped. Knowing the shape from the schema — rather than from the runtime value — keeps the
 * {@code structuredContent} shape stable per tool and predictable for app authors.
 *
 * @see AppStructuredContent
 */
public enum DeclaredSchemaKind {
    /** The response declares a JSON object; the parsed body is passed through as-is. */
    OBJECT,
    /** The response declares an array or a scalar; the parsed body is wrapped under {@code result}. */
    NON_OBJECT,
    /** The response declares nothing determinable; the shape is decided from the runtime value. */
    UNKNOWN
}
