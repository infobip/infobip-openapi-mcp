package com.infobip.openapi.mcp.openapi.tool.naming;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;

/**
 * Strategy interface for generating tool names from OpenAPI operations.
 * <p>
 * This interface defines the contract for naming strategies that convert OpenAPI operations
 * into human-readable tool names for the MCP (Model Context Protocol) server.
 * Different implementations can provide various naming approaches such as using operation IDs,
 * endpoint patterns, or custom formatting rules.
 * </p>
 *
 * <h3>Available Implementations:</h3>
 * <ul>
 *   <li>{@code OperationIdStrategy} - Uses the operationId from OpenAPI specification as-is</li>
 *   <li>{@code SanitizedOperationIdStrategy} - Uses operationId with sanitization (lowercase, underscore replacement)</li>
 *   <li>{@code EndpointStrategy} - Constructs names from HTTP method and path</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <p>
 * Naming strategies are configured via application properties and created by the
 * {@code NamingStrategyFactory}. They can be wrapped with additional functionality
 * like length trimming using decorators such as {@code TrimNamingStrategy}.
 * </p>
 *
 * @see OperationIdStrategy
 * @see SanitizedOperationIdStrategy
 * @see EndpointStrategy
 * @see NamingStrategyFactory
 * @see TrimNamingStrategy
 */
public interface NamingStrategy {

    /**
     * Generates a tool name for the given OpenAPI operation.
     * <p>
     * The generated name should be suitable for use as an MCP tool identifier.
     * Different implementations may apply various formatting rules, validations,
     * and transformations to ensure the resulting name meets specific requirements.
     * </p>
     *
     * @param operation the full OpenAPI operation containing HTTP method, path, operation details, and OpenAPI specification
     * @return the generated tool name for the operation
     * @throws IllegalArgumentException if the operation cannot be processed or contains invalid data
     *         (e.g., missing operationId when required, invalid characters, etc.)
     */
    String name(FullOperation operation);
}
