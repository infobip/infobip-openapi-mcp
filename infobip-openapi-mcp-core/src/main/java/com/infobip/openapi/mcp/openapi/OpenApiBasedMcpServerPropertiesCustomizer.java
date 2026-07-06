package com.infobip.openapi.mcp.openapi;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.InitializingBean;

public class OpenApiBasedMcpServerPropertiesCustomizer implements InitializingBean {

    @Nullable
    private final McpServerProperties properties;

    private final McpServerMetaData metaData;

    public OpenApiBasedMcpServerPropertiesCustomizer(
            @Nullable McpServerProperties properties, McpServerMetaData metaData) {
        this.properties = properties;
        this.metaData = metaData;
    }

    @Override
    public void afterPropertiesSet() {
        if (properties == null) {
            return;
        }

        properties.setName(metaData.getName());
        properties.setVersion(metaData.getVersion());
        properties.setInstructions(metaData.getInstructions());
    }
}
