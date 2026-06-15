package com.infobip.openapi.mcp.prompt;

/**
 * Represents a prompt argument definition from the {@code x-mcp-prompts} OpenAPI vendor extension.
 *
 * @param name        the argument name
 * @param description a human-readable description of the argument
 * @param required    whether the argument is required; defaults to false
 */
record PromptExtensionArgument(String name, String description, Boolean required) {

    PromptExtensionArgument {
        if (required == null) {
            required = false;
        }
    }
}
