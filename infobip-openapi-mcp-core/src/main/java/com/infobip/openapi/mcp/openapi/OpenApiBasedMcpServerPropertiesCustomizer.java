package com.infobip.openapi.mcp.openapi;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;

public class OpenApiBasedMcpServerPropertiesCustomizer {

    @Nullable
    private final McpServerProperties properties;

    private final McpServerMetaData metaData;

    public OpenApiBasedMcpServerPropertiesCustomizer(
            @Nullable McpServerProperties properties, McpServerMetaData metaData) {
        this.properties = properties;
        this.metaData = metaData;
    }

    @PostConstruct
    public void customizeProperties() {
        if (properties == null) {
            return;
        }

        properties.setName(metaData.getName());
        properties.setVersion(metaData.getVersion());
        properties.setInstructions(metaData.getInstructions());
    }
}
