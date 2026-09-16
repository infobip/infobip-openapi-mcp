package com.infobip.openapi.mcp.openapi.schema;

import io.swagger.v3.oas.models.Operation;
import java.util.Set;

public class Spec {
    private Spec() {}

    static final String MCP_EXAMPLE_EXTENSION = "x-mcp-example";
    public static final String MCP_ANNOTATIONS_EXTENSION = "x-mcp-annotations";
    public static final String MCP_PROMPTS_EXTENSION = "x-mcp-prompts";
    public static final String MCP_RESOURCE_EXTENSION = "x-mcp-resource";

    static final Set<String> SUPPORTED_PARAMETER_TYPES = Set.of(
            DecomposedRequestData.ParametersByType.QUERY,
            DecomposedRequestData.ParametersByType.PATH,
            DecomposedRequestData.ParametersByType.HEADER,
            DecomposedRequestData.ParametersByType.COOKIE);

    /**
     * Determines whether the given OpenAPI operation is flagged as an MCP resource via the
     * {@value #MCP_RESOURCE_EXTENSION} vendor extension.
     *
     * @param operation the OpenAPI operation to check
     * @return {@code true} if the operation carries a truthy {@value #MCP_RESOURCE_EXTENSION} extension
     */
    public static boolean isResourceOperation(Operation operation) {
        if (operation.getExtensions() == null) {
            return false;
        }
        return Boolean.TRUE.equals(operation.getExtensions().get(MCP_RESOURCE_EXTENSION));
    }

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
