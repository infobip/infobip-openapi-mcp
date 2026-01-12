package com.infobip.openapi.mcp.config;

import org.jspecify.annotations.Nullable;

/**
 * Configuration for API base URL that supports multiple resolution strategies:
 * <ul>
 *   <li>Explicit URL string - directly use the provided URL</li>
 *   <li>Integer index - select the i-th server from OpenAPI servers array (0-indexed)</li>
 *   <li>Null/not provided - use the first server from OpenAPI servers array</li>
 * </ul>
 */
public sealed interface ApiBaseUrlConfig {

    /**
     * Explicit URL provided by the user.
     */
    record Explicit(String url) implements ApiBaseUrlConfig {}

    /**
     * Index into the OpenAPI servers array (0-indexed).
     */
    record ServerIndex(int index) implements ApiBaseUrlConfig {}

    /**
     * Use default (first server from OpenAPI spec).
     */
    record Default() implements ApiBaseUrlConfig {}

    /**
     * Parse from configuration value.
     * Supports: String URL, Integer index, or null for default.
     *
     * @param value the configuration value (can be null, string URL, or string integer)
     * @return the parsed configuration
     * @throws IllegalArgumentException if the value is an integer but negative
     */
    static ApiBaseUrlConfig parse(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return new Default();
        }

        try {
            int index = Integer.parseInt(value.trim());
            if (index < 0) {
                throw new IllegalArgumentException("Server index must be non-negative, got: " + index);
            }
            return new ServerIndex(index);
        } catch (NumberFormatException e) {
            try {
                new java.net.URI(value);
            } catch (Exception uriException) {
                throw new IllegalArgumentException("Invalid URL format: " + value, uriException);
            }
            return new Explicit(value);
        }
    }
}
