package com.infobip.openapi.mcp.prompt;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Represents a single prompt definition from the {@code x-mcp-prompts} OpenAPI vendor extension.
 *
 * <p>Exactly one of {@code resolve} (resolved mode — backend resolution) or {@code messages}
 * (inline mode — Mustache templates) must be present. Providing both or neither is a
 * configuration error.
 *
 * @param name        the unique prompt name
 * @param description a human-readable description of what the prompt does
 * @param arguments   ordered list of arguments the prompt accepts
 * @param resolve     configuration for the backend endpoint that resolves this prompt (resolved mode)
 * @param messages    inline message templates rendered server-side (inline mode)
 */
record PromptExtensionDefinition(
        String name,
        String description,
        List<PromptExtensionArgument> arguments,
        @Nullable PromptResolveConfig resolve,
        @Nullable List<PromptMessageDefinition> messages) {

    PromptExtensionDefinition {
        if (arguments == null) {
            arguments = List.of();
        }
        if (resolve != null && messages != null) {
            throw new IllegalArgumentException(
                    "Prompt '" + name + "' must define either 'resolve' or 'messages', not both");
        }
        if (resolve == null && messages == null) {
            throw new IllegalArgumentException("Prompt '" + name
                    + "' must define either 'resolve' (backend resolution) or 'messages' (inline templates)");
        }
        if (messages != null && messages.isEmpty()) {
            throw new IllegalArgumentException("Prompt '" + name + "' defines 'messages' but the list is empty");
        }
    }
}
