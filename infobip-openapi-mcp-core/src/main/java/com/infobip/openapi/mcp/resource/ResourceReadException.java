package com.infobip.openapi.mcp.resource;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NonNull;

public final class ResourceReadException extends McpError {

    private ResourceReadException(int errorCode, String message, Throwable cause) {
        super(McpError.builder(errorCode).message(message).build().getJsonRpcError());
        if (cause != null) {
            initCause(cause);
        }
    }

    public static @NonNull ResourceReadException becauseBackendCallFailed(
            String resourceName, String path, Throwable cause) {
        return new ResourceReadException(
                McpSchema.ErrorCodes.INTERNAL_ERROR,
                "Failed to read resource '" + resourceName + "' via GET " + path + ": " + cause.getMessage(),
                cause);
    }
}
