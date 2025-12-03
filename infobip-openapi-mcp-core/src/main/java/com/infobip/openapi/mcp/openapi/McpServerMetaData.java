package com.infobip.openapi.mcp.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.core.env.PropertyResolver;

public class McpServerMetaData {

    private final String name;
    private final String version;

    @Nullable
    private final String instructions;

    public McpServerMetaData(PropertyResolver environment, OpenApiRegistry registry) {
        var apiInfo =
                Optional.ofNullable(registry).map(OpenApiRegistry::openApi).map(OpenAPI::getInfo);

        name = extractEnvProp(environment, "name")
                .or(() -> apiInfo.map(Info::getTitle))
                .orElse("mcp-server");

        version = extractEnvProp(environment, "version")
                .or(() -> apiInfo.map(Info::getVersion))
                .orElse("1.0.0");

        instructions = extractEnvProp(environment, "instructions")
                .or(() -> apiInfo.map(Info::getDescription).or(() -> apiInfo.map(Info::getSummary)))
                .orElse(null);
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public @Nullable String getInstructions() {
        return instructions;
    }

    private Optional<String> extractEnvProp(PropertyResolver environment, String propName) {
        var fullPropKey = "%s.%s".formatted(McpServerProperties.CONFIG_PREFIX, propName);
        try {
            var value = environment.getProperty(fullPropKey);
            return Optional.ofNullable(value);
        } catch (RuntimeException ignored) {
            // Failed to resolve the property, e.g. it references
            // a system environment variable that doesn't exist
            return Optional.empty();
        }
    }
}
