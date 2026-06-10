package com.infobip.openapi.mcp.prompt;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Represents a single inline message in an inline mode (static template) prompt definition.
 *
 * @param role    the message role
 * @param content the message content, which may contain Mustache template placeholders
 */
record PromptMessageDefinition(McpSchema.Role role, String content) {}
