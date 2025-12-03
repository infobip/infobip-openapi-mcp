package com.infobip.openapi.mcp.openapi.tool;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record FullOperation(String path, PathItem.HttpMethod method, Operation operation, OpenAPI openApi) {}
