package com.infobip.openapi.mcp.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class OpenApiMapperFactory {

    public ObjectMapper mapper(OpenAPI openApi) {
        return switch (openApi.getSpecVersion()) {
            case SpecVersion.V30 -> Json.mapper();
            case SpecVersion.V31 -> Json31.mapper();
        };
    }
}
