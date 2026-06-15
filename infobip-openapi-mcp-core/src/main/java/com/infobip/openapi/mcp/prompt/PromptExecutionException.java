package com.infobip.openapi.mcp.prompt;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Exception thrown when a prompt cannot be executed.
 *
 * <p>Each factory method maps to the appropriate MCP JSON-RPC error code:
 * <ul>
 *   <li>{@link #becauseMissingRequiredArguments} — {@code INVALID_PARAMS} (client error)</li>
 *   <li>{@link #becauseBackendCallFailed} and {@link #becauseBackendResponseInvalid} — {@code INTERNAL_ERROR}</li>
 * </ul>
 */
public final class PromptExecutionException extends McpError {

    private PromptExecutionException(int errorCode, String message, Throwable cause) {
        super(McpError.builder(errorCode).message(message).build().getJsonRpcError());
        if (cause != null) {
            initCause(cause);
        }
    }

    public static @NonNull PromptExecutionException becauseMissingRequiredArguments(
            String promptName, List<String> missingArguments) {
        return new PromptExecutionException(
                McpSchema.ErrorCodes.INVALID_PARAMS,
                "Missing required arguments for prompt '" + promptName + "': " + missingArguments,
                null);
    }

    public static @NonNull PromptExecutionException becauseBackendCallFailed(
            String promptName, String path, Throwable cause) {
        return new PromptExecutionException(
                McpSchema.ErrorCodes.INTERNAL_ERROR,
                "Failed to resolve prompt '" + promptName + "' via GET " + path + ": " + cause.getMessage(),
                cause);
    }

    public static @NonNull PromptExecutionException becauseBackendResponseInvalid(String promptName, Throwable cause) {
        return new PromptExecutionException(
                McpSchema.ErrorCodes.INTERNAL_ERROR,
                "Failed to parse prompt resolve response for '" + promptName + "'",
                cause);
    }
}
