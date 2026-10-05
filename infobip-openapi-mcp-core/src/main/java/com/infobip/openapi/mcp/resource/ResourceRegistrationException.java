package com.infobip.openapi.mcp.resource;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import org.jspecify.annotations.NonNull;

public final class ResourceRegistrationException extends RuntimeException {

    private ResourceRegistrationException(String message) {
        super(message);
    }

    private ResourceRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public static @NonNull ResourceRegistrationException becauseNonGetMethodMarkedAsResource(
            @NonNull FullOperation operation) {
        return new ResourceRegistrationException(String.format(
                "Unable to register resource for operation: %s %s. "
                        + "Only GET operations can be marked with the 'x-mcp-resource' extension.",
                operation.method(), operation.path()));
    }

    public static @NonNull ResourceRegistrationException becauseNonGetMethodMarkedAsAppResource(
            @NonNull FullOperation operation) {
        return new ResourceRegistrationException(String.format(
                "Unable to register MCP app resource for operation: %s %s. "
                        + "Only GET operations can be marked with the 'x-mcp-app-resource' extension.",
                operation.method(), operation.path()));
    }

    public static @NonNull ResourceRegistrationException becauseAppResourceOperationHasParameters(
            @NonNull FullOperation operation) {
        return new ResourceRegistrationException(String.format(
                "Unable to register MCP app resource for operation: %s %s. "
                        + "MCP app resources must not declare path or query parameters.",
                operation.method(), operation.path()));
    }

    public static @NonNull ResourceRegistrationException becauseMarkedAsAppResourceAndAppTool(
            @NonNull FullOperation operation) {
        return new ResourceRegistrationException(String.format(
                "Unable to register MCP app resource for operation: %s %s. "
                        + "An operation cannot be marked with both 'x-mcp-app-resource' and 'x-mcp-app'.",
                operation.method(), operation.path()));
    }

    public static @NonNull ResourceRegistrationException becauseInvalidAppResourceExtension(
            @NonNull FullOperation operation, Throwable cause) {
        return new ResourceRegistrationException(
                String.format(
                        "Unable to register MCP app resource for operation: %s %s. "
                                + "Invalid 'x-mcp-app-resource' extension: %s",
                        operation.method(), operation.path(), cause.getMessage()),
                cause);
    }

    public static @NonNull ResourceRegistrationException becauseDuplicateAppResourceUri(
            @NonNull String uri, @NonNull FullOperation firstOperation, @NonNull FullOperation secondOperation) {
        return new ResourceRegistrationException(String.format(
                "Unable to register MCP app resource '%s'. " + "It is declared by both operations: %s %s and %s %s.",
                uri, firstOperation.method(), firstOperation.path(), secondOperation.method(), secondOperation.path()));
    }

    public static @NonNull ResourceRegistrationException becauseNameCannotBeDetermined(
            @NonNull FullOperation operation, Throwable cause) {
        return new ResourceRegistrationException(
                String.format(
                        "Unable to register resource for operation: %s %s. Error determining resource name: %s",
                        operation.method(), operation.path(), cause.getMessage()),
                cause);
    }
}
