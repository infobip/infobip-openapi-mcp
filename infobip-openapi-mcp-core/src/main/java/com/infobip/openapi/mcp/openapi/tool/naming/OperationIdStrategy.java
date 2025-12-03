package com.infobip.openapi.mcp.openapi.tool.naming;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;

/**
 * Naming strategy that uses the operation ID from the OpenAPI specification as the name for the operation.
 * <p>
 * This strategy provides a direct mapping from OpenAPI operationId to MCP tool names,
 * preserving the exact operationId value without any modifications. It's ideal when
 * you want to maintain strict consistency with your OpenAPI documentation and the
 * operationIds are already in a suitable format for tool names.
 * </p>
 *
 * <h3>Behavior:</h3>
 * <ul>
 *   <li>Returns the operationId exactly as specified in the OpenAPI document</li>
 *   <li>No sanitization or transformation is applied</li>
 *   <li>Preserves original casing, spaces, and special characters</li>
 *   <li>Validates that operationId is present and not empty</li>
 * </ul>
 *
 * <h3>Validation:</h3>
 * <p>This strategy performs strict validation and will throw exceptions for:</p>
 * <ul>
 *   <li>Null operationId - when the operation doesn't have an operationId field set</li>
 *   <li>Empty operationId - when the operationId is an empty string or contains only whitespace</li>
 * </ul>
 *
 * <h3>Examples:</h3>
 * <ul>
 *   <li>{@code "getUserById"} → {@code "getUserById"}</li>
 *   <li>{@code "CreateUserProfile"} → {@code "CreateUserProfile"}</li>
 *   <li>{@code "send-sms-message"} → {@code "send-sms-message"}</li>
 *   <li>{@code "Update User Settings"} → {@code "Update User Settings"}</li>
 * </ul>
 *
 * <h3>When to Use:</h3>
 * <ul>
 *   <li>Your OpenAPI operationIds are already in the desired format</li>
 *   <li>You want to maintain exact consistency with OpenAPI documentation</li>
 *   <li>Your tooling can handle the original operationId format</li>
 *   <li>You need predictable mapping from OpenAPI to tool names</li>
 * </ul>
 *
 * @see NamingStrategy
 * @see SanitizedOperationIdStrategy
 * @see EndpointStrategy
 */
public class OperationIdStrategy implements NamingStrategy {

    /**
     * Returns the operation ID from the OpenAPI specification as the tool name.
     * <p>
     * This method extracts the operationId from the operation and returns it without
     * any modifications. It performs validation to ensure the operationId is present
     * and not empty, throwing descriptive exceptions if validation fails.
     * </p>
     *
     * @param operation the full OpenAPI operation containing the operationId
     * @return the operationId exactly as specified in the OpenAPI document
     * @throws IllegalArgumentException if operationId is null with message
     *         "Operation ID is null - cannot determine how to proceed with naming."
     * @throws IllegalArgumentException if operationId is empty or whitespace-only with message
     *         "Operation ID is empty or contains only whitespace - cannot determine how to proceed with naming."
     */
    @Override
    public String name(FullOperation operation) {
        String operationId = operation.operation().getOperationId();

        if (operationId == null) {
            throw new IllegalArgumentException("Operation ID is null - cannot determine how to proceed with naming.");
        }

        if (operationId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Operation ID is empty or contains only whitespace - cannot determine how to proceed with naming.");
        }

        return operationId;
    }
}
