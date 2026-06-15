package com.infobip.openapi.mcp.prompt;

/**
 * Configuration for the backend endpoint that resolves a prompt with the provided arguments.
 *
 * <p>Arguments are forwarded as query parameters on a GET request.
 *
 * @param path the endpoint path or URL. Can be a relative path starting with {@code /} (resolved
 *             against the API base URL) or an absolute URL starting with {@code http://} or
 *             {@code https://} to target a different server.
 */
record PromptResolveConfig(String path) {

    private static final java.util.regex.Pattern SCHEME_PATTERN = java.util.regex.Pattern.compile("^(https?://)");

    PromptResolveConfig {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("resolve path must not be null or blank");
        }
        if (!path.startsWith("/") && !SCHEME_PATTERN.matcher(path).find()) {
            throw new IllegalArgumentException(
                    "resolve path must start with '/' (relative) or 'http[s]://' (absolute), got: " + path);
        }
    }

    boolean isAbsolute() {
        return SCHEME_PATTERN.matcher(path).find();
    }
}
