package com.infobip.openapi.mcp.prompt;

import java.util.Map;

/**
 * Represents a single prompt definition from the {@code x-mcp-prompts} OpenAPI vendor extension.
 *
 * @param description a human-readable description of what the prompt does
 * @param arguments   named arguments the prompt accepts; keys are argument names
 * @param resolve     configuration for the backend endpoint that resolves this prompt
 */
record PromptExtensionDefinition(
        String description, Map<String, PromptExtensionArgument> arguments, PromptResolveConfig resolve) {

    PromptExtensionDefinition {
        if (arguments == null) {
            arguments = Map.of();
        }
    }
}
