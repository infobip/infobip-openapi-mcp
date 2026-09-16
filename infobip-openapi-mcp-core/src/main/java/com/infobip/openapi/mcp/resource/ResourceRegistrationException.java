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

    public static @NonNull ResourceRegistrationException becauseNameCannotBeDetermined(
            @NonNull FullOperation operation, Throwable cause) {
        return new ResourceRegistrationException(
                String.format(
                        "Unable to register resource for operation: %s %s. Error determining resource name: %s",
                        operation.method(), operation.path(), cause.getMessage()),
                cause);
    }
}
