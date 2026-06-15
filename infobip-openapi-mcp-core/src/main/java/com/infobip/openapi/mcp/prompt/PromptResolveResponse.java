package com.infobip.openapi.mcp.prompt;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;

/**
 * Response DTO for the backend prompt resolution endpoint.
 * <p>
 * The backend returns a JSON object with a description and a list of messages,
 * matching the MCP {@code GetPromptResult} structure:
 * <pre>
 * {
 *   "description": "Greet a user",
 *   "messages": [
 *     {"role": "user", "content": "Generate a greeting for Alice."},
 *     {"role": "assistant", "content": "Hello Alice, welcome!"}
 *   ]
 * }
 * </pre>
 *
 * @param description a description of the prompt result
 * @param messages    the resolved prompt messages
 */
record PromptResolveResponse(String description, List<PromptResolveMessage> messages) {

    PromptResolveResponse {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("prompt resolve response must contain at least one message");
        }
        for (int i = 0; i < messages.size(); i++) {
            var msg = messages.get(i);
            if (msg.content() == null || msg.content().isBlank()) {
                throw new IllegalArgumentException(
                        "prompt resolve response message at index " + i + " has blank content");
            }
        }
    }

    /**
     * A single message in the prompt resolution response.
     *
     * @param role    the message role
     * @param content the message text content
     */
    record PromptResolveMessage(McpSchema.Role role, String content) {

        PromptResolveMessage {
            if (role == null) {
                throw new IllegalArgumentException("prompt resolve response message role must not be null");
            }
        }
    }
}
