package com.infobip.openapi.mcp.prompt;

/**
 * Configuration for the backend endpoint that resolves a prompt with the provided arguments.
 *
 * @param path   the endpoint path, relative to the API base URL
 * @param method the HTTP method to use (GET or POST)
 */
record PromptResolveConfig(String path, String method) {}
