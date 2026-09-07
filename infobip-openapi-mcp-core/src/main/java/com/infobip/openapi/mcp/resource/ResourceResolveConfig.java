package com.infobip.openapi.mcp.resource;

/**
 * Configuration for the backend endpoint that resolves a resource.
 *
 * <p>For resource templates, RFC 6570 variables extracted from the requested URI are forwarded to
 * the backend. A variable whose name appears as a {@code {placeholder}} in {@code path} is
 * substituted into the path; any remaining variable is appended as a query parameter. The backend
 * response body becomes the resource content verbatim (raw passthrough); {@code mimeType} comes
 * from the {@code x-mcp-resources} definition, falling back to the response {@code Content-Type}
 * header.
 *
 * @param path the endpoint path or URL. Can be a relative path starting with {@code /} (resolved
 *             against the API base URL) or an absolute URL starting with {@code http://} or
 *             {@code https://} to target a different server. May contain {@code {placeholder}}
 *             segments that are substituted from the resource template variables.
 */
record ResourceResolveConfig(String path) {

    private static final java.util.regex.Pattern SCHEME_PATTERN = java.util.regex.Pattern.compile("^(https?://)");

    ResourceResolveConfig {
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
