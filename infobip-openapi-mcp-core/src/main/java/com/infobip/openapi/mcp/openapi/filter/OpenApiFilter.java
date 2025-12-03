package com.infobip.openapi.mcp.openapi.filter;

import io.swagger.v3.oas.models.OpenAPI;
import org.jspecify.annotations.NullMarked;

/**
 * This interface allows the modification or filtering of the initial OpenAPI specification
 * prior to MCP capabilities generation.
 */
@NullMarked
public interface OpenApiFilter {

    /**
     * Filters the provided OpenAPI specification.
     *
     * @param openApi the OpenAPI specification to filter
     * @return the filtered OpenAPI specification
     */
    OpenAPI filter(OpenAPI openApi);

    /**
     * Returns the name of the filter.
     * This name is used to identify the filter in the configuration.
     *
     * @return the name of the filter
     */
    default String name() {
        return this.getClass().getSimpleName();
    }
}
