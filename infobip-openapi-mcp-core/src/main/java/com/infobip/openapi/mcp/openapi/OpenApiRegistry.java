package com.infobip.openapi.mcp.openapi;

import com.infobip.openapi.mcp.config.OpenApiMcpProperties;
import com.infobip.openapi.mcp.openapi.filter.OpenApiFilterChain;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenApiRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiRegistry.class);

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
        LOGGER.info("Loading OpenAPI from {}.", openApiMcpProperties.openApiUrl());
        try {
            var newUneditedOpenApi = openApiReader.read(openApiMcpProperties.openApiUrl());
            if (this.openApi != null && isSameOpenApiVersion(openApi, newUneditedOpenApi)) {
                LOGGER.info("No new OpenAPI found, skipping reload.");
                return;
            }
            var newFilteredOpenApi = openApiFilterChain.filter(newUneditedOpenApi);
            openApi = openApiResolver.resolve(newFilteredOpenApi);
            LOGGER.info("Successfully loaded OpenAPI from {}.", openApiMcpProperties.openApiUrl());
        } catch (RuntimeException e) {
            LOGGER.error("Failed to load OpenAPI from {}: {}", openApiMcpProperties.openApiUrl(), e.getMessage(), e);
            throw e;
        }
    }

    public OpenAPI openApi() {
        return openApi;
    }

    private boolean isSameOpenApiVersion(OpenAPI openApi1, OpenAPI openApi2) {
        var openApiVersion1 = openApi1.getInfo().getVersion();
        var openApiVersion2 = openApi2.getInfo().getVersion();
        return openApiVersion1.equals(openApiVersion2);
    }
}
