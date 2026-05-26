package com.infobip.openapi.mcp.prompt;

/**
 * Represents a single inline message in an inline mode (static template) prompt definition.
 *
 * @param role    the message role ({@code "user"} or {@code "assistant"})
 * @param content the message content, which may contain Mustache template placeholders
 */
record PromptMessageDefinition(String role, String content) {}
