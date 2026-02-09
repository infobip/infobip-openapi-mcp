package com.infobip.openapi.mcp.openapi;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.filter.OpenApiFilterChain;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenApiRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiRegistry.class);

    private static final String DEFAULT_OPENAPI_VERSION = "-1.0.0";

    private final OpenApiMcpProperties openApiMcpProperties;
    private final OpenApiReader openApiReader;
    private final OpenApiFilterChain openApiFilterChain;
    private final OpenApiResolver openApiResolver;

    private OpenAPI openApi;

    public OpenApiRegistry(
            OpenApiMcpProperties openApiMcpProperties,
            OpenApiReader openApiReader,
            OpenApiFilterChain openApiFilterChain,
            OpenApiResolver openApiResolver) {
        this.openApiMcpProperties = openApiMcpProperties;
        this.openApiReader = openApiReader;
        this.openApiFilterChain = openApiFilterChain;
        this.openApiResolver = openApiResolver;
        reload();
    }

    public void reload() {
        reload(false);
    }

    public void reloadWithUpdateCheck() {
        reload(true);
    }

    private void reload(boolean checkIfUpdated) {
        LOGGER.info("Loading OpenAPI from {}.", openApiMcpProperties.openApiUrl());
        try {
            var currentOpenApiVersion = openApi != null ? openApi.getInfo().getVersion() : DEFAULT_OPENAPI_VERSION;
            var originalOpenApi = openApiReader.read(openApiMcpProperties.openApiUrl());
            if (checkIfUpdated && !isOpenApiUpdated(currentOpenApiVersion, originalOpenApi)) {
                return;
            }

            var filteredOpenApi = openApiFilterChain.filter(originalOpenApi);
            openApi = openApiResolver.resolve(filteredOpenApi);
            LOGGER.info("Successfully loaded OpenAPI from {}.", openApiMcpProperties.openApiUrl());
        } catch (RuntimeException e) {
            LOGGER.error("Failed to load OpenAPI from {}: {}", openApiMcpProperties.openApiUrl(), e.getMessage(), e);
            throw e;
        }
    }

    public OpenAPI openApi() {
        return openApi;
    }

    private boolean isOpenApiUpdated(String currentOpenApiVersion, OpenAPI loadedOpenAPI) {
        if (this.openApi == null || currentOpenApiVersion.equals(DEFAULT_OPENAPI_VERSION)) {
            return true;
        }
        return !loadedOpenAPI.getInfo().getVersion().equals(currentOpenApiVersion);
    }
}
