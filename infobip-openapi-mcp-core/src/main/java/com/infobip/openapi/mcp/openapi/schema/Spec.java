package com.infobip.openapi.mcp.openapi.schema;

import io.swagger.v3.oas.models.Operation;
import java.util.Set;

public class Spec {
    private Spec() {}

    static final String MCP_EXAMPLE_EXTENSION = "x-mcp-example";
    public static final String MCP_ANNOTATIONS_EXTENSION = "x-mcp-annotations";
    public static final String MCP_PROMPTS_EXTENSION = "x-mcp-prompts";
    public static final String MCP_RESOURCE_EXTENSION = "x-mcp-resource";
    public static final String MCP_APP_RESOURCE_EXTENSION = "x-mcp-app-resource";
    public static final String MCP_APP_EXTENSION = "x-mcp-app";

    static final Set<String> SUPPORTED_PARAMETER_TYPES = Set.of(
            DecomposedRequestData.ParametersByType.QUERY,
            DecomposedRequestData.ParametersByType.PATH,
            DecomposedRequestData.ParametersByType.HEADER,
            DecomposedRequestData.ParametersByType.COOKIE);

    /**
     * Determines whether the given OpenAPI operation is flagged as an MCP resource via the
     * {@value #MCP_RESOURCE_EXTENSION} vendor extension, or as an MCP Apps UI resource via the
     * {@value #MCP_APP_RESOURCE_EXTENSION} vendor extension. Operations for which this returns
     * {@code true} are excluded from the tool list.
     *
     * @param operation the OpenAPI operation to check
     * @return {@code true} if the operation carries a truthy {@value #MCP_RESOURCE_EXTENSION} or
     *         {@value #MCP_APP_RESOURCE_EXTENSION} extension
     */
    public static boolean isResourceOperation(Operation operation) {
        return Boolean.TRUE.equals(extensionValue(operation, MCP_RESOURCE_EXTENSION))
                || isAppResourceOperation(operation);
    }

    /**
     * Determines whether the given OpenAPI operation is flagged as an MCP Apps UI resource via the
     * {@value #MCP_APP_RESOURCE_EXTENSION} vendor extension. The extension value is either the
     * shorthand {@code true} or an object with the UI resource configuration.
     *
     * @param operation the OpenAPI operation to check
     * @return {@code true} if the operation carries a truthy {@value #MCP_APP_RESOURCE_EXTENSION} extension
     */
    public static boolean isAppResourceOperation(Operation operation) {
        var value = extensionValue(operation, MCP_APP_RESOURCE_EXTENSION);
        return value != null && !Boolean.FALSE.equals(value);
    }

    /**
     * Determines whether the given OpenAPI operation links itself to an MCP Apps UI resource via the
     * {@value #MCP_APP_EXTENSION} vendor extension.
     *
     * @param operation the OpenAPI operation to check
     * @return {@code true} if the operation carries an {@value #MCP_APP_EXTENSION} extension
     */
    public static boolean isAppToolOperation(Operation operation) {
        return extensionValue(operation, MCP_APP_EXTENSION) != null;
    }

    private static Object extensionValue(Operation operation, String extensionName) {
        if (operation.getExtensions() == null) {
            return null;
        }
        return operation.getExtensions().get(extensionName);
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
