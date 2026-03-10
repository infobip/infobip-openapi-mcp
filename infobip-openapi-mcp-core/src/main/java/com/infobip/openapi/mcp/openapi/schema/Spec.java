package com.infobip.openapi.mcp.openapi.schema;

import java.util.Set;

public class Spec {
    private Spec() {}

    static final String MCP_EXAMPLE_EXTENSION = "x-mcp-example";
    public static final String MCP_ANNOTATIONS_EXTENSION = "x-mcp-annotations";

    static final Set<String> SUPPORTED_PARAMETER_TYPES = Set.of(
            DecomposedRequestData.ParametersByType.QUERY,
            DecomposedRequestData.ParametersByType.PATH,
            DecomposedRequestData.ParametersByType.HEADER,
            DecomposedRequestData.ParametersByType.COOKIE);

    /**
     * Controls how request examples from the OpenAPI specification are appended to MCP tool descriptions.
     */
    public enum ExamplesMode {
        /** No examples are appended to tool descriptions. */
        SKIP,
        /** All examples from the OpenAPI specification are appended. */
        ALL,
        /**
         * Only examples explicitly annotated with {@code x-mcp-example: true} on the OpenAPI
         * {@code Example} object are appended, giving fine-grained control over what reaches
         * MCP tool descriptions.
         */
        ANNOTATED
    }
}
