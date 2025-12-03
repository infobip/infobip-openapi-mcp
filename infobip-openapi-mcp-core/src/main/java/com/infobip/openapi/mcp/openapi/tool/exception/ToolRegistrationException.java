package com.infobip.openapi.mcp.openapi.tool.exception;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import org.jspecify.annotations.NonNull;

/**
 * Exception thrown when a tool cannot be registered.
 */
public final class ToolRegistrationException extends RuntimeException {

    private ToolRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public static @NonNull ToolRegistrationException becauseOfJsonSchemaProcessingError(
            String operationId, Throwable cause) {
        return new ToolRegistrationException(
                String.format(
                        "Unable to register tool for operation: %s. " + "Error processing JSON schema: %s",
                        operationId, cause.getMessage()),
                cause);
    }

    public static @NonNull ToolRegistrationException becauseNameCannotBeDetermined(
            @NonNull FullOperation operation, Throwable cause) {
        return new ToolRegistrationException(
                String.format(
                        "Unable to register tool for operation: %s %s. " + "Error determining tool name: %s",
                        operation.method(), operation.path(), cause.getMessage()),
                cause);
    }
}
