/**
 * Tool naming strategies for OpenAPI operations in the MCP (Model Context Protocol) server.
 * <p>
 * This package provides a flexible naming strategy system for converting OpenAPI operations
 * into appropriate tool names for the MCP server. The system follows the Strategy pattern
 * to allow different naming approaches based on configuration requirements.
 * </p>
 *
 * <h3>Core Components:</h3>
 * <ul>
 *   <li>{@link com.infobip.openapi.mcp.openapi.tool.naming.NamingStrategy} - Strategy interface defining the naming contract</li>
 *   <li>{@link com.infobip.openapi.mcp.openapi.tool.naming.NamingStrategyType} - Enumeration of available strategy types</li>
 *   <li>{@link com.infobip.openapi.mcp.openapi.tool.naming.NamingStrategyFactory} - Factory for creating strategy instances</li>
 * </ul>
 *
 * <h3>Available Strategies:</h3>
 * <dl>
 *   <dt><strong>Operation ID Strategy</strong> ({@link com.infobip.openapi.mcp.openapi.tool.naming.OperationIdStrategy})</dt>
 *   <dd>Uses the operationId from OpenAPI specification as-is, preserving original formatting</dd>
 *
 *   <dt><strong>Sanitized Operation ID Strategy</strong> ({@link com.infobip.openapi.mcp.openapi.tool.naming.SanitizedOperationIdStrategy})</dt>
 *   <dd>Uses operationId with comprehensive sanitization (lowercase, underscore replacement)</dd>
 *
 *   <dt><strong>Endpoint Strategy</strong> ({@link com.infobip.openapi.mcp.openapi.tool.naming.EndpointStrategy})</dt>
 *   <dd>Constructs names from HTTP method and path information</dd>
 * </dl>
 *
 * <h3>Decorators:</h3>
 * <ul>
 *   <li>{@link com.infobip.openapi.mcp.openapi.tool.naming.TrimNamingStrategy} - Trims names to a maximum length</li>
 * </ul>
 *
 * <h3>Configuration:</h3>
 * <p>
 * Naming strategies are configured through application properties and automatically
 * instantiated by the Spring configuration. The factory supports applying decorators
 * such as length trimming based on configuration parameters.
 * </p>
 *
 * <h3>Null Safety:</h3>
 * <p>
 * This package is marked with {@code @NullMarked}, meaning all types are non-null by default
 * unless explicitly marked with {@code @Nullable}. This provides compile-time null safety
 * and better IDE support for detecting potential null pointer exceptions.
 * </p>
 *
 * @see com.infobip.openapi.mcp.openapi.tool.ToolRegistry
 * @see com.infobip.openapi.mcp.config.OpenApiMcpProperties.Tools.Naming
 */
@NullMarked
package com.infobip.openapi.mcp.openapi.tool.naming;

import org.jspecify.annotations.NullMarked;
