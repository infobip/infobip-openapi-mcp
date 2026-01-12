package com.infobip.openapi.mcp.config;

import com.infobip.openapi.mcp.openapi.OpenApiRegistry;
import io.swagger.v3.oas.models.servers.Server;
import java.net.URI;
import java.util.List;

/**
 * Resolves the API base URL based on configuration and OpenAPI specification.
 * This resolver supports three resolution strategies:
 * <ul>
 *   <li>Explicit URL: Directly use the configured URL string</li>
 *   <li>Server index: Select a specific server by index from OpenAPI servers array</li>
 *   <li>Default: Use the first server from OpenAPI servers array</li>
 * </ul>
 */
public class ApiBaseUrlProvider {

    private final OpenApiRegistry openApiRegistry;

    private URI apiBaseUrl;

    public ApiBaseUrlProvider(ApiBaseUrlConfig config, OpenApiRegistry openApiRegistry) {
        this.openApiRegistry = openApiRegistry;
        switch (config) {
            case ApiBaseUrlConfig.Explicit explicit -> parseUri(explicit.url());
            case ApiBaseUrlConfig.ServerIndex serverIndex -> resolveFromServerIndex(serverIndex.index());
            case ApiBaseUrlConfig.Default() -> resolveFromServerIndex(0);
        }
    }

    /**
     * Get the resolved API base URL.
     *
     * @return The resolved base URL as URI
     */
    public URI get() {
        return apiBaseUrl;
    }

    private void resolveFromServerIndex(int index) {
        List<Server> servers = openApiRegistry.openApi().getServers();

        if (servers == null || servers.isEmpty()) {
            throw new ApiBaseUrlResolutionException("No servers defined in OpenAPI specification. "
                    + "Please either define servers in your OpenAPI spec or provide an explicit api-base-url.");
        }

        if (index >= servers.size() || index < 0) {
            throw new ApiBaseUrlResolutionException(String.format(
                    "Server index %d is out of bounds. OpenAPI spec has %d server(s) defined. "
                            + "Valid indices are 0-%d.",
                    index, servers.size(), servers.size() - 1));
        }

        var server = servers.get(index);
        var url = server.getUrl();

        if (url == null || url.isBlank()) {
            throw new ApiBaseUrlResolutionException(String.format("Server at index %d has no URL defined", index));
        }

        parseUri(url);
    }

    private void parseUri(String url) {
        try {
            this.apiBaseUrl = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new ApiBaseUrlResolutionException("Invalid URL: " + url + ". " + e.getMessage(), e);
        }
    }

    /**
     * Exception thrown when API base URL resolution fails.
     */
    public static class ApiBaseUrlResolutionException extends RuntimeException {
        public ApiBaseUrlResolutionException(String message) {
            super(message);
        }

        public ApiBaseUrlResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
