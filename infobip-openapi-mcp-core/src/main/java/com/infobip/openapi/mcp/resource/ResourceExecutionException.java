package com.infobip.openapi.mcp.resource;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NonNull;

/**
 * Exception thrown when a resource cannot be read.
 *
 * <p>Each factory method maps to the appropriate MCP JSON-RPC error code:
 * <ul>
 *   <li>{@link #becauseNotFound} — {@code RESOURCE_NOT_FOUND}</li>
 *   <li>{@link #becauseBackendCallFailed} — {@code INTERNAL_ERROR}</li>
 * </ul>
 */
public final class ResourceExecutionException extends McpError {

    private ResourceExecutionException(int errorCode, String message, Throwable cause) {
        super(McpError.builder(errorCode).message(message).build().getJsonRpcError());
        if (cause != null) {
            initCause(cause);
        }
    }

    public static @NonNull ResourceExecutionException becauseNotFound(String resourceUri) {
        return new ResourceExecutionException(
                McpSchema.ErrorCodes.RESOURCE_NOT_FOUND, "Resource not found: " + resourceUri, null);
    }

    public static @NonNull ResourceExecutionException becauseBackendCallFailed(
            String resourceUri, String path, Throwable cause) {
        return new ResourceExecutionException(
                McpSchema.ErrorCodes.INTERNAL_ERROR,
                "Failed to resolve resource '" + resourceUri + "' via GET " + path + ": " + cause.getMessage(),
                cause);
    }
}
