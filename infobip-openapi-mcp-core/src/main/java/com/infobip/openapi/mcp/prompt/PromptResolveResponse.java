package com.infobip.openapi.mcp.prompt;

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

    /**
     * A single message in the prompt resolution response.
     *
     * @param role    the message role ({@code "user"} or {@code "assistant"})
     * @param content the message text content
     */
    record PromptResolveMessage(String role, String content) {}
}
